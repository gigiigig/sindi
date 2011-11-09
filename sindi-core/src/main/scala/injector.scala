//      _____         ___  
//     / __(_)__  ___/ (_)
//    _\ \/ / _ \/ _  / /
//   /___/_/_//_/\_,_/_/
//
//  (c) 2011, Alois Cochard
//
//  http://aloiscochard.github.com/sindi
//

package sindi
package injector

import scala.Stream._
import scala.util.control.Exception._

object `package` {
  type Binding = Tuple2[AnyRef, binder.binding.provider.Provider[AnyRef]]
  type Injection[T] = () => T
}

case class Qualifiers(q: AnyRef, next: Option[Qualifiers] = None) {
  def or(that: AnyRef) = Qualifiers(that, Some(this))
  def ||(that: AnyRef) = or(that)
}

object Injector {
  def apply(bindings : List[Binding]): Injector =
    new DefaultInjector(bindings)
  def apply(bindings : List[Binding], parent: () => Injector): Injector = 
    new ChildedInjector(bindings, parent)
}

/** An interface containing operations for object injection. */
trait Injector {
  /** Return the object associated with a given type. */
  final def inject[T <: AnyRef : Manifest]: T =
    injection[T].apply
  /** Return the object associated with a given type and qualifiers. */
  final def injectAs[T <: AnyRef : Manifest](qualifiers: Qualifiers): T =
    injectionAs[T](qualifiers).apply
  /** Return all objects associated with a given type. */
  final def injectAll[T <: AnyRef : Manifest]: Stream[T] =
    injectionAll[T].map(_.apply)
  /** Return all objects associated with a given type and qualifiers. */
  final def injectAll[T <: AnyRef : Manifest](qualifiers: Qualifiers): Stream[T] =
    injectionAll[T](qualifiers).map(_.apply)

  /** Return the injection associated with a given type. */
  final def injection[T <: AnyRef : Manifest]: Injection[T] =
    injectionAs[T](Qualifiers(None))
  /** Return all injections associated with a given type. */
  final def injectionAll[T <: AnyRef : Manifest]: Stream[Injection[T]] =
    injectionAll[T](Qualifiers(None))

  /** Return the injection associated with a given type and qualifiers. */
  def injectionAs[T <: AnyRef : Manifest](qualifiers: Qualifiers): Injection[T]
  /** Return all injections associated with a given type and qualifiers. */
  def injectionAll[T <: AnyRef : Manifest](qualifiers: Qualifiers): Stream[Injection[T]]
}

private trait Bindable extends Injector {
  val bindings : List[Binding]

  override def injectionAs[T <: AnyRef : Manifest](qualifiers: Qualifiers) = () =>
    qualifiers.next.flatMap(qualifiers => catching(classOf[TypeNotBoundException]).opt(injectAs[T](qualifiers))).getOrElse {
      bindings.view.filter(isBound(_)(manifest[T])(qualifiers.q))
        .map { case (q, p) => p().asInstanceOf[T] }
        .headOption.getOrElse {
          val q = if (qualifiers == None) { "" } else { " with qualifiers %s".format(qualifiers) }
          throw TypeNotBoundException(("Unable to inject %s" + q + ": type is not bound.").format(manifest[T]))
        }
    }

  override def injectionAll[T <: AnyRef : Manifest](qualifiers: Qualifiers) = bindings.toStream
    .filter(isBound(_)(manifest[T])(qualifiers))
    .map { case (_, p) => () => p().asInstanceOf[T] }

  private def isBound(manifest: Manifest[_])(b: Binding): Boolean = b._2.signature <:< manifest

  private def isBound(qualifiers: Qualifiers)(b: Binding): Boolean = qualifiers.next match {
    case Some(q) => isBound(q)(b) || b._1 == qualifiers.q
    case None => b._1 == qualifiers.q
  }

  private def isBound(b: Binding): Manifest[_] => Qualifiers => Boolean = {
    // TODO Find a generalization for this (look at |+| on Arrows in scalaz)
    def f[A, B, C](ac: A => C)(bc: B => C)(ccc: (C, C) => C)(a: A)(b: B): C = ccc(ac(a), bc(b))
    f(isBound(_: Manifest[_])(b))(isBound(_: Qualifiers)(b))(_ && _) _
  }
}

private trait Childable extends Injector {
  protected val parent: () => Injector

  abstract override def injectionAs[T <: AnyRef : Manifest](qualifiers: Qualifiers) =
    catching(classOf[TypeNotBoundException]).opt(parent().injectionAs[T](qualifiers)).getOrElse(super.injectionAs[T](qualifiers))

  abstract override def injectionAll[T <: AnyRef : Manifest](qualifiers: Qualifiers) =
    super.injectionAll[T](qualifiers).append(parent().injectionAll[T](qualifiers))
}

private class DefaultInjector(override val bindings : List[Binding])
  extends Injector with Bindable

private class ChildedInjector(override val bindings : List[Binding], override val parent: () => Injector)
  extends DefaultInjector(bindings) with Childable
