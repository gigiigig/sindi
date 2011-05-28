package sindi

// TODO [aloiscochard] qualifier fallback: test use of ||
// TODO [aloiscochard] @default annotation for defImpl or other strategy ?
// TODO [aloiscochard] map to config[file]
// TODO [aloiscochard] Add assertion and error message
// TODO [aloiscochard] Add assertion check on context.bindings when locked

object Sindi extends context.Context with context.Configurable

class Component[S <: AnyRef : Manifest] extends Context {
  def this(context: Context) = { this(); childify(context) }
  def apply(): S = inject[S]
}

trait Context extends context.Context with context.Childifiable with context.Configurable {
  override protected def default = () => sindi.injector.Injector(bindings, () => Sindi.injector)
}

