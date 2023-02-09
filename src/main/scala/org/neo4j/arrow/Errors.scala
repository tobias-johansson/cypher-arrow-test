package org.neo4j.arrow

import org.neo4j.cypher.internal.util.symbols.CypherType

object Errors {

  def notImplemented(t: CypherType): Nothing =
    throw new NotImplementedError(s"no support for $t")

}
