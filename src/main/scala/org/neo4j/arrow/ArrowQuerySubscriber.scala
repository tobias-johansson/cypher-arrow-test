package org.neo4j.arrow

import org.apache.arrow.memory.BufferAllocator
import org.apache.arrow.vector.BigIntVector
import org.apache.arrow.vector.BitVector
import org.apache.arrow.vector.VarCharVector
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.complex.StructVector
import org.apache.arrow.vector.complex.UnionVector
import org.apache.arrow.vector.complex.impl.UnionWriter
import org.apache.arrow.vector.util.Text
import org.neo4j.arrow.Errors.notImplemented
import org.neo4j.cypher.internal.util.symbols.AnyType
import org.neo4j.cypher.internal.util.symbols.BooleanType
import org.neo4j.cypher.internal.util.symbols.IntegerType
import org.neo4j.cypher.internal.util.symbols.NodeType
import org.neo4j.graphdb.QueryStatistics
import org.neo4j.kernel.impl.query.QuerySubscriber
import org.neo4j.values.AnyValue
import org.neo4j.values.storable.BooleanValue
import org.neo4j.values.storable.IntegralValue
import org.neo4j.values.storable.NoValue
import org.neo4j.values.virtual.VirtualNodeReference

class ArrowQuerySubscriber(signature: QuerySignature, allocator: BufferAllocator) extends QuerySubscriber {

  val root: VectorSchemaRoot = new ArrowVectorSchemaRootFactory(allocator).forRecord(signature)

  private var row = -1;

  def pushRecord(record: Array[AnyValue]): Unit = {
    onRecord()
    record.indices.foreach(i => onField(i, record(i)))
    onRecordCompleted()
  }

  override def onResult(numberOfFields: Int): Unit = {
    if (numberOfFields != root.getSchema.getFields.size()) {
      throw new IllegalStateException("wrong number of fields")
    }
  }

  override def onRecord(): Unit =
    row += 1

  override def onField(i: Int, value: AnyValue): Unit = {
    val vector = root.getVector(i)
    val col = signature.columns(i)
    value match {
      case _: NoValue => ()
      case _ => col.cypherType match {

        case t: BooleanType =>
          vector.asInstanceOf[BitVector].setSafe(row, booleanValue(value))

        case t: IntegerType =>
          vector.asInstanceOf[BigIntVector].setSafe(row, longValue(value))

        case t: NodeType =>
          writeNode(vector.asInstanceOf[StructVector], value.asInstanceOf[VirtualNodeReference])

        case t: AnyType =>
          val vec = vector.asInstanceOf[UnionVector]
          val writer = vec.getWriter.asInstanceOf[UnionWriter]
          writer.setPosition(row)
          value match {
            case v: BooleanValue => writer.writeBit(booleanValue(v))
            case v: IntegralValue => writer.writeBigInt(longValue(v))
            case v: VirtualNodeReference =>
              writer.asStruct().varChar("elementId")
              writeNode(vec.getStruct, v)
            case v => notImplemented(t)
          }

        case t =>
          notImplemented(t)
      }
    }
  }

  def writeNode(vec: StructVector, node: VirtualNodeReference): Unit = {
    val elementId = new Text(node.elementId())
    vec.getChild("elementId").asInstanceOf[VarCharVector].setSafe(row, elementId)
    vec.setIndexDefined(row)
  }

  def booleanValue(value: AnyValue): Int =
    booleanIntValue(value.asInstanceOf[BooleanValue].booleanValue())

  def booleanIntValue(boolean: Boolean): Int =
    if (boolean) 1 else 0

  def longValue(value: AnyValue): Long =
    value.asInstanceOf[IntegralValue].longValue()

  override def onRecordCompleted(): Unit =
    ()

  override def onError(throwable: Throwable): Unit =
    ???

  def rowCount: Int = row + 1

  override def onResultCompleted(queryStatistics: QueryStatistics): Unit =
    root.setRowCount(rowCount)

}

