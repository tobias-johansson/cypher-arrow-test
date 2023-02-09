package org.neo4j.arrow

import org.apache.arrow.memory.BufferAllocator
import org.apache.arrow.vector.BigIntVector
import org.apache.arrow.vector.BitVector
import org.apache.arrow.vector.FieldVector
import org.apache.arrow.vector.VarCharVector
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.complex.StructVector
import org.apache.arrow.vector.complex.UnionVector
import org.apache.arrow.vector.types.UnionMode
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.Field
import org.apache.arrow.vector.types.pojo.FieldType
import org.neo4j.arrow.Errors.notImplemented
import org.neo4j.cypher.internal.util.symbols.AnyType
import org.neo4j.cypher.internal.util.symbols.BooleanType
import org.neo4j.cypher.internal.util.symbols.CypherType
import org.neo4j.cypher.internal.util.symbols.IntegerType
import org.neo4j.cypher.internal.util.symbols.NodeType

import scala.jdk.CollectionConverters.IterableHasAsJava

class ArrowVectorSchemaRootFactory(allocator: BufferAllocator) {

  def forRecord(signature: QuerySignature): VectorSchemaRoot =
    new VectorSchemaRoot(signature.columns.map(forEntry).toSeq.asJava)

  def forEntry(col: ColumnSignature): FieldVector = {
    val factory = new VectorFactory(col.name)
    col.cypherType match {
      case t: BooleanType =>
        factory.boolVector()

      case t: IntegerType =>
        factory.integerVector()

      case t: NodeType =>
        factory.nodeVector()

      case t: AnyType =>
        val vec = new UnionVector(col.name, allocator, fieldTypes.any, null)
        val inner = new VectorFactory("")
        vec.addVector(inner.boolVector())
        vec.addVector(inner.integerVector())
        vec.addVector(inner.nodeVector())
        vec

      case t => notImplemented(t)
    }
  }

  class VectorFactory(name: String) {
    def boolVector() =
      new BitVector(new Field(name, fieldTypes.bool, null), allocator)

    def integerVector() =
      new BigIntVector(new Field(name, fieldTypes.integer, null), allocator)

    def nodeVector() = {
      val vec = new StructVector(name, allocator, fieldTypes.node, null)
      vec.addOrGet("elementId", new FieldType(false, ArrowType.Utf8.INSTANCE, null), classOf[VarCharVector])
      vec
    }
  }


  object fieldTypes {
    val bool: FieldType =
      FieldType.nullable(ArrowType.Bool.INSTANCE)

    val integer: FieldType =
      FieldType.nullable(new ArrowType.Int(64, true))

    val node: FieldType =
      FieldType.nullable(ArrowType.Struct.INSTANCE)

    val any: FieldType =
      FieldType.nullable(new ArrowType.Union(UnionMode.Sparse, Array(
        bool.getType.getTypeID.ordinal(),
        integer.getType.getTypeID.ordinal(),
        node.getType.getTypeID.ordinal(),
      )))
  }

}