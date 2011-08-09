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
package binder

import binder.binding._

trait Scoper {
  def thread: () => AnyRef = () => java.lang.Thread.currentThread
}

trait Binder extends Scoper {
  def bind[T <: AnyRef : Manifest](provider: => T): Binding[T] = Binding[T](() => provider)
  def scopify[T <: AnyRef](binding: Binding[T])(scoper: () => Any): Binding[T] = Binding[T](binding, scoper)
  def qualify[T <: AnyRef](binding: Binding[T], qualifier: AnyRef): Binding[T] = Binding[T](binding, qualifier)
}

trait DSL {
  protected object Bindings { def apply(bindings: binding.Binding[_]*): List[binding.Binding[_]] = bindings.toList }

  def bind[T <: AnyRef : Manifest] = new BindSource[T]

  protected class BindSource[T <: AnyRef : Manifest] extends Binder {
    def to(provider: => T) = new SimpleBind[T](provider)
  }

  protected sealed abstract class Bind[T <: AnyRef : Manifest] extends Binder {
    def build: Binding[T]
    protected def toQualified(qualifier: AnyRef): QualifiedBind[T] = new QualifiedBind[T](this, qualifier)
    protected def toScopable(scoper: () => Any): ScopedBind[T] = new ScopedBind[T](this, scoper)
  }

  protected trait Qualifiable[T <: AnyRef] extends Bind[T] { def as(qualifier: AnyRef) = toQualified(qualifier) }
  protected trait Scopable[T <: AnyRef] extends Bind[T] { def scope(scoper: => Any) = toScopable(() => scoper) }
  
  protected class SimpleBind[T <: AnyRef : Manifest](provider: => T) extends Bind[T] with Qualifiable[T] with Scopable[T] {
    override def build = bind(provider)
  }

  protected class ScopedBind[T <: AnyRef : Manifest](bind: Bind[T], scoper: () => Any) extends Bind[T] {
    override def build = scopify(bind.build)(scoper)
  }

  protected class QualifiedBind[T <: AnyRef : Manifest](bind: Bind[T], qualifier: AnyRef) extends Bind[T] with Scopable[T] {
    override def build = qualify(bind.build, qualifier)
  }

  protected implicit def bind2binding[T <: AnyRef : Manifest](bind: Bind[T]): Binding[T] = bind.build
  protected implicit def bind2bindings[T <: AnyRef : Manifest](bind: Bind[T]): List[Binding[_]] = List(bind.build)
}