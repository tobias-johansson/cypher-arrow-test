package org.neo4j.arrow

import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.ipc.ArrowStreamReader
import org.apache.arrow.vector.ipc.ArrowStreamWriter
import org.apache.commons.io.output.ByteArrayOutputStream
import org.neo4j.cypher.internal.util.symbols.CTAny
import org.neo4j.cypher.internal.util.symbols.CTBoolean
import org.neo4j.cypher.internal.util.symbols.CTInteger
import org.neo4j.cypher.internal.util.symbols.CTNode
import org.neo4j.kernel.api.DefaultElementIdMapperV1
import org.neo4j.kernel.database.DatabaseIdFactory
import org.neo4j.kernel.impl.query.QuerySubscriber
import org.neo4j.values.AnyValue
import org.neo4j.values.storable.NoValue
import org.neo4j.values.storable.Values
import org.neo4j.values.virtual.VirtualValues

import java.io.ByteArrayInputStream
import java.nio.channels.Channels
import java.util.UUID

object Test {

  def main(args: Array[String]): Unit = {

    println("---- writing")
    val bytes = write()

    println("---- reading")
    read(bytes)

  }

  def write(): Array[Byte] = {

    val allocator = new RootAllocator()
    val signature = QuerySignature("int" -> CTInteger, "bool" -> CTBoolean, "node" -> CTNode, "any" -> CTAny)

    val subscriber = new ArrowQuerySubscriber(signature, allocator)
    println(s"write schema: ${subscriber.root.getSchema}")

    val out = new ByteArrayOutputStream();
    val writer = new ArrowStreamWriter(subscriber.root, null, Channels.newChannel(out));

    writer.start()

    val mapper = new DefaultElementIdMapperV1(DatabaseIdFactory.from("mydb", UUID.randomUUID()))
    object vals {
      val none = NoValue.NO_VALUE
      val n1 = VirtualValues.node(1, mapper)
      val n2 = VirtualValues.node(2, mapper)
      val i123 = Values.longValue(123)
      val i456 = Values.longValue(456)
      val i789 = Values.longValue(789)
      val bFalse = Values.booleanValue(false)
      val bTrue = Values.booleanValue(true)
    }
    subscriber.pushRecord(Array(vals.i123, vals.bFalse, vals.n1, vals.i123))
    subscriber.pushRecord(Array(vals.none, vals.bTrue, vals.n2, vals.bTrue))
    subscriber.pushRecord(Array(vals.i456, vals.none, vals.none, vals.none))
    subscriber.pushRecord(Array(vals.i789, vals.bTrue, vals.none, vals.n2))
    subscriber.onResultCompleted(null)

    writer.writeBatch()

    val bytes = out.toByteArray
    println(s"wrote bytes: ${bytes.size}")
    bytes
  }

  def read(bytes: Array[Byte]): Unit = {
    val allocator = new RootAllocator()

    val reader = new ArrowStreamReader(new ByteArrayInputStream(bytes), allocator)
    val readRoot = reader.getVectorSchemaRoot;
    println(s"read schema: ${readRoot.getSchema}")
    reader.loadNextBatch()

    readRoot.getFieldVectors.forEach(vector => {
      println(vector.getField.getName)
      for {i <- 0.until(readRoot.getRowCount)} {
        println("- " + vector.getObject(i))
      }
    })
  }

  def pushRecord(subscriber: QuerySubscriber, record: Array[AnyValue]): Unit = {
    subscriber.onRecord()
    record.indices.foreach(i => subscriber.onField(i, record(i)))
    subscriber.onRecordCompleted()
  }


}
