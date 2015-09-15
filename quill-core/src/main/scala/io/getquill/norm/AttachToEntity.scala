package io.getquill.norm

import io.getquill.ast._
import io.getquill.util.Messages.fail

object AttachToEntity {

  def apply(f: (Query, Ident) => Query, alias: Option[Ident] = None)(q: Query): Query =
    q match {

      case Map(a: Entity, b, c)     => Map(f(a, b), b, c)
      case FlatMap(a: Entity, b, c) => FlatMap(f(a, b), b, c)
      case Filter(a: Entity, b, c)  => Filter(f(a, b), b, c)
      case SortBy(a: Entity, b, c)  => SortBy(f(a, b), b, c)

      case Map(a: Query, b, c)      => Map(apply(f, Some(b))(a), b, c)
      case FlatMap(a: Query, b, c)  => FlatMap(apply(f, Some(b))(a), b, c)
      case Filter(a: Query, b, c)   => Filter(apply(f, Some(b))(a), b, c)
      case SortBy(a: Query, b, c)   => SortBy(apply(f, Some(b))(a), b, c)
      case Reverse(a: Query)        => Reverse(apply(f, alias)(a))
      case Take(a: Query, b)        => Take(apply(f, alias)(a), b)
      case Drop(a: Query, b)        => Drop(apply(f, alias)(a), b)

      case e: Entity                => f(e, alias.getOrElse(Ident("x")))

      case other                    => fail(s"Can't find an 'Entity' in '$q'")
    }
}
