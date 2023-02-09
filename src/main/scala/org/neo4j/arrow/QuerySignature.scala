package org.neo4j.arrow

import org.neo4j.cypher.internal.util.symbols.CypherType

case class ColumnSignature(name: String, cypherType: CypherType)

case class QuerySignature(columns: Array[ColumnSignature])

object QuerySignature {
  def apply(columns: (String, CypherType)*): QuerySignature =
    new QuerySignature(columns.map((ColumnSignature.apply _).tupled).toArray)
}