package arrow.core.extensions

import arrow.Kind
import arrow.Kind2
import arrow.core.Either
import arrow.core.Eval
import arrow.core.ForIor
import arrow.core.Ior
import arrow.core.IorOf
import arrow.core.IorPartialOf
import arrow.core.Tuple2
import arrow.core.ap
import arrow.core.extensions.ior.monad.monad
import arrow.core.fix
import arrow.core.flatMap
import arrow.core.toT
import arrow.extension
import arrow.typeclasses.Applicative
import arrow.typeclasses.Apply
import arrow.typeclasses.Bifoldable
import arrow.typeclasses.Bifunctor
import arrow.typeclasses.Bitraverse
import arrow.typeclasses.Eq
import arrow.typeclasses.Foldable
import arrow.typeclasses.Functor
import arrow.typeclasses.Hash
import arrow.typeclasses.Monad
import arrow.typeclasses.MonadSyntax
import arrow.typeclasses.Semigroup
import arrow.typeclasses.Semigroupal
import arrow.typeclasses.Show
import arrow.typeclasses.Traverse
import arrow.undocumented

@extension
@undocumented
interface IorFunctor<L> : Functor<IorPartialOf<L>> {
  override fun <A, B> Kind<IorPartialOf<L>, A>.map(f: (A) -> B): Ior<L, B> = fix().map(f)
}

@extension
interface IorBifunctor : Bifunctor<ForIor> {
  override fun <A, B, C, D> Kind2<ForIor, A, B>.bimap(fl: (A) -> C, fr: (B) -> D): Kind2<ForIor, C, D> =
    fix().bimap(fl, fr)
}

@extension
interface IorApply<L> : Apply<IorPartialOf<L>>, IorFunctor<L> {

  fun SL(): Semigroup<L>

  override fun <A, B> Kind<IorPartialOf<L>, A>.map(f: (A) -> B): Ior<L, B> = fix().map(f)

  override fun <A, B> Kind<IorPartialOf<L>, A>.ap(ff: Kind<IorPartialOf<L>, (A) -> B>): Ior<L, B> =
    fix().ap(SL(), ff)
}

@extension
interface IorApplicative<L> : Applicative<IorPartialOf<L>>, IorFunctor<L>, IorApply<L> {

  override fun SL(): Semigroup<L>

  override fun <A> just(a: A): Ior<L, A> = Ior.Right(a)

  override fun <A, B> Kind<IorPartialOf<L>, A>.map(f: (A) -> B): Ior<L, B> = fix().map(f)

  override fun <A, B> Kind<IorPartialOf<L>, A>.ap(ff: Kind<IorPartialOf<L>, (A) -> B>): Ior<L, B> =
    fix().ap(SL(), ff)
}

@extension
interface IorMonad<L> : Monad<IorPartialOf<L>>, IorApplicative<L> {

  override fun SL(): Semigroup<L>

  override fun <A, B> Kind<IorPartialOf<L>, A>.map(f: (A) -> B): Ior<L, B> = fix().map(f)

  override fun <A, B> Kind<IorPartialOf<L>, A>.flatMap(f: (A) -> Kind<IorPartialOf<L>, B>): Ior<L, B> =
    fix().flatMap(SL()) { f(it).fix() }

  override fun <A, B> Kind<IorPartialOf<L>, A>.ap(ff: Kind<IorPartialOf<L>, (A) -> B>): Ior<L, B> =
    fix().ap(SL(), ff)

  override fun <A, B> tailRecM(a: A, f: (A) -> IorOf<L, Either<A, B>>): Ior<L, B> =
    Ior.tailRecM(a, f, SL())
}

@extension
interface IorFoldable<L> : Foldable<IorPartialOf<L>> {

  override fun <B, C> Kind<IorPartialOf<L>, B>.foldLeft(b: C, f: (C, B) -> C): C = fix().foldLeft(b, f)

  override fun <B, C> Kind<IorPartialOf<L>, B>.foldRight(lb: Eval<C>, f: (B, Eval<C>) -> Eval<C>): Eval<C> =
    fix().foldRight(lb, f)
}

@extension
interface IorTraverse<L> : Traverse<IorPartialOf<L>>, IorFoldable<L> {

  override fun <G, B, C> IorOf<L, B>.traverse(AP: Applicative<G>, f: (B) -> Kind<G, C>): Kind<G, Ior<L, C>> =
    fix().traverse(AP, f)
}

@extension
interface IorSemigroupal<L> : Semigroupal<IorPartialOf<L>> {
  fun SL(): Semigroup<L>

  override fun <A, B> Kind<IorPartialOf<L>, A>.product(fb: Kind<IorPartialOf<L>, B>): Kind<IorPartialOf<L>, Tuple2<A, B>> =
    fb.fix().ap(SL(), fix().map { a: A -> { b: B -> a toT b } })
}

@extension
interface IorBifoldable : Bifoldable<ForIor> {
  override fun <A, B, C> IorOf<A, B>.bifoldLeft(c: C, f: (C, A) -> C, g: (C, B) -> C): C =
    fix().bifoldLeft(c, f, g)

  override fun <A, B, C> IorOf<A, B>.bifoldRight(c: Eval<C>, f: (A, Eval<C>) -> Eval<C>, g: (B, Eval<C>) -> Eval<C>): Eval<C> =
    fix().bifoldRight(c, f, g)
}

@extension
interface IorBitraverse : Bitraverse<ForIor>, IorBifoldable {
  override fun <G, A, B, C, D> IorOf<A, B>.bitraverse(AP: Applicative<G>, f: (A) -> Kind<G, C>, g: (B) -> Kind<G, D>): Kind<G, IorOf<C, D>> =
    fix().let {
      AP.run {
        it.fold({ f(it).map { Ior.Left(it) } }, { g(it).map { Ior.Right(it) } },
          { a, b -> map(f(a), g(b)) { Ior.Both(it.a, it.b) } })
      }
    }
}

@extension
interface IorEq<L, R> : Eq<Ior<L, R>> {

  fun EQL(): Eq<L>

  fun EQR(): Eq<R>

  override fun Ior<L, R>.eqv(b: Ior<L, R>): Boolean = when (this) {
    is Ior.Left -> when (b) {
      is Ior.Both -> false
      is Ior.Right -> false
      is Ior.Left -> EQL().run { value.eqv(b.value) }
    }
    is Ior.Both -> when (b) {
      is Ior.Left -> false
      is Ior.Both -> EQL().run { leftValue.eqv(b.leftValue) } && EQR().run { rightValue.eqv(b.rightValue) }
      is Ior.Right -> false
    }
    is Ior.Right -> when (b) {
      is Ior.Left -> false
      is Ior.Both -> false
      is Ior.Right -> EQR().run { value.eqv(b.value) }
    }
  }
}

@extension
interface IorShow<L, R> : Show<Ior<L, R>> {
  override fun Ior<L, R>.show(): String =
    toString()
}

@extension
interface IorHash<L, R> : Hash<Ior<L, R>>, IorEq<L, R> {

  fun HL(): Hash<L>
  fun HR(): Hash<R>

  override fun EQL(): Eq<L> = HL()

  override fun EQR(): Eq<R> = HR()

  override fun Ior<L, R>.hash(): Int = when (this) {
    is Ior.Left -> HL().run { value.hash() }
    is Ior.Right -> HR().run { value.hash() }
    is Ior.Both -> 31 * HL().run { leftValue.hash() } + HR().run { rightValue.hash() }
  }
}

fun <L, R> Ior.Companion.fx(SL: Semigroup<L>, c: suspend MonadSyntax<IorPartialOf<L>>.() -> R): Ior<L, R> =
  Ior.monad(SL).fx.monad(c).fix()
