def add(x: Int, y: Int) = x + y

// println(add(3, 5))

def addCurried(x: Int)(y: Int) = x + y

// println(addCurried(3)(5))

val add2: Function1[Int, Int] = addCurried(2)

// println(add2(3))

///////////////////////////////////////////////////////////////////////////////


def fNoArgs = println("fNoArgs got called")

// fNoArgs
// fNoArgs
// fNoArgs


///////////////////////////////////////////////////////////////////////////////

implicit val x: String = "up here"

def implicitString()(implicit x: String) = x

// println(implicitString()) // up here

///////////////////////////////////////////////////////////////////////////////

implicit val booleanInstance:   Boolean = true
implicit val intInstance:       Int     = 5

def primativeImplicitly[T](implicit x: T): T  = {
  x
}

// println(primativeImplicitly[Boolean]) // true
// println(primativeImplicitly[Int]) // 5

///////////////////////////////////////////////////////////////////////////////

implicit val listIntInstance:   List[Int]   = List(1)
implicit val optionInstance:    Option[Int] = Some(1)

def hktImplicitly[F[_]](implicit x: F[Int]): F[Int]  = {
  x
}

// scala> :k List
// List's kind is F[+A]

// println(hktImplicitly[List])
// println(hktImplicitly[Option])

///////////////////////////////////////////////////////////////////////////////

type Id[A] = A
// * -> *

implicit val listStringInstance:   List[String]   = List("hello")
implicit val listBoolInstance:     List[Boolean]  = List(true)

def hktAppImplicitly[F[_], A](implicit x: F[A]): F[A]  = {
  x
}

// println(hktAppImplicitly[List, String])
// println(hktAppImplicitly[List, Boolean])
// println(hktAppImplicitly[Id, String])

///////////////////////////////////////////////////////////////////////////////

// generally good practise to wrap implicit instances in an object so you can conditionally import their extension methods
object IntSyntax {
  implicit final class IntExtensions(private val self: Int) extends AnyVal {
    def increment(): Int = self + 1
  }
}
// https://kotlinlang.org/docs/reference/extensions.html

import IntSyntax._

// println(5.increment()) // 6

///////////////////////////////////////////////////////////////////////////////

case class Json(innerString: String)

trait Encode[A] {
  def encode(x: A): Json
}

object EncodeInstances {
  implicit val encodeString: Encode[String] = new Encode[String] {
    override def encode(x: String) = Json("\"" + x.toString() + "\"")
  }

  implicit val encodeInt: Encode[Int] = new Encode[Int] {
    override def encode(x: Int) = Json(x.toString())
  }

  implicit val encodeBoolean: Encode[Boolean] = new Encode[Boolean] {
    override def encode(x: Boolean) = Json(x.toString())
  }

  implicit def encodeMap[A, B]: Encode[Map[String, Json]] = new Encode[Map[String, Json]] {
    override def encode(kv: Map[String, Json]) = {
      val inner = 
        kv
          .toList
          .map { case (k, v) => s"${encodeString.encode(k).innerString}: ${v.innerString}" }
          .mkString(", ")

      val outer = s"{ ${inner} }"
      Json(outer)
    }
  }
}

object EncodeSyntax {
  implicit class EncodeIdExtensions[A](private val self: A) extends AnyVal {
    def encode()(implicit instance: Encode[A]): Json = {
      instance.encode(self)
    }
  }
}

////////////////////////////////////////////////////////////////////////////////
// YOUR CONCERNS

import EncodeInstances._
import EncodeSyntax._

case class Person(name: String, age: Int, alive: Boolean)


// at this point in type, we break object orientation.
// We've defined the Encode[Person] _seperate_ (!) from Person.
// We've seperated behaviour and data.
//
// The practical implication of this is that Person could be a 3rd party
// datatype, and we could still define an Encode instance for it

implicit def encodePerson: Encode[Person] = new Encode[Person] {
    override def encode(person: Person): Json = 
      // we can obviously do this in a macro
      Map(
        "name" -> person.name.encode(),
        "age" -> person.age.encode(),
        "alive" -> person.alive.encode(),
      ).encode()
}

val me = Person(name="Max Bo", age=22, alive=true)

// println(me.encode().innerString)

def needsAnEncoder[A](a: A)(implicit instance: Encode[A]) {
  println(a.encode().innerString)
}

// is syntactically equivalent to

def needsAnEncoderPrime[A: Encode](a: A) {
  // val instance = implicitly[Encode[A]] // we can still recover the instance
  println(a.encode().innerString)
}

// needsAnEncoder(me)

case class HasNoEncoder()

// hkts.sc:150: could not find implicit value for evidence parameter of type ammonite.$file.hkts.Encode[ammonite.$file.hkts.HasNoEncoder]
// val res_28 = needsAnEncoder(HasNoEncoder())
// needsAnEncoder(HasNoEncoder())

////////////////////////////////////////////////////////////////////////////////

// what if we want the `.map` to work on not just lists, but anything?

case class Foo[T](a: T)

trait Functor[F[_]] {
  def map[A, B](fa: F[A])(f: A => B): F[B]
}

object FunctorInstances {
    // @ List(1, 2, 3).map(x => x + 1)
  // res0: List[Int] = List(2, 3, 4)
  implicit val listFunctorInstance: Functor[List] = new Functor[List] {
    def map[A, B](fa: List[A])(f: A => B): List[B] = fa.map(f)
  }

  // our custom function for our container (we can define Functor instances for anything we want!)
  implicit val fooFunctorInstance: Functor[Foo] = new Functor[Foo] {
    def map[A, B](fa: Foo[A])(f: A => B): Foo[B] = Foo(f(fa.a))
  }
}

object FunctorSyntax {
  implicit final class FunctorExtensions[F[_], A](private val self: F[A]) extends AnyVal {
    def map[B](f: A => B)(implicit instance: Functor[F]): F[B] = {
      instance.map(self)(f)
    }
  }
}

import FunctorInstances._
import FunctorSyntax._

// println(List(1, 2, 3).map((x: Int) => x + 1)) // List(2, 3, 4)
// println(Foo(1).map((x: Int) => x + 1)) // 6

def incrementAll[F[_]: Functor](xs: F[Int]): F[Int] = {
  xs.map(_ + 1)
}

///////////////////////////////////////////////////////////////////////////////

trait Monad[F[_]] {
  def pure[A](a: A): F[A]
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
}

object MonadInstances {
  implicit val listMonadInstance: Monad[List] = new Monad[List] {
    def pure[A](a: A): List[A] = List(a)
    def flatMap[A, B](fa: List[A])(f: A => List[B]): List[B] = fa.flatMap(f)
  }

  implicit val fooMonadInstance: Monad[Foo] = new Monad[Foo] {
    def pure[A](a: A): Foo[A] = Foo(a)
    def flatMap[A, B](fa: Foo[A])(f: A => Foo[B]): Foo[B] = f(fa.a)
  }
}

object MonadSyntax {
  implicit class MonadIdExtensions[A](private val self: A) extends AnyVal {
    def pure[F[_]]()(implicit instance: Monad[F]): F[A] = {
      instance.pure(self)
    }
  }

  implicit final class MonadExtensions[F[_], A](private val self: F[A]) extends AnyVal {
    def flatMap[B](f: A => F[B])(implicit instance: Monad[F]): F[B] = {
      instance.flatMap(self)(f)
    }
  }
}

import FunctorInstances._
import FunctorSyntax._
import MonadInstances._
import MonadSyntax._

val manualListComprehension = 
  List(0, 1, 2).flatMap(n => 
    List(n * 10, n * 100).flatMap(big => 
      List((n, big))
    )
  )

// println(manualListComprehension)

// using `for` comprehension, we get a nicely sugared form

val listComprehension: List[(Int, Int)] = for {
  n    <- List(0, 1, 2)
  big  <- List(n * 10, n * 100)
} yield (n, big)

// println(listComprehension)

// Effectively identitical to list comprehensions in Python
// ```
// list_comprehension = [ (n, big) 
//   for n in [0, 1, 2] 
//   for big in [n * 10, n * 100 ] 
// ]
// ```
//

val fooComprehension: Foo[(Int, Int)] = for {
  x <- Foo(5)
  y <- Foo(x + 6)
} yield (x, y)

// println(fooComprehension)

val bailsOutOnNone: Option[(Int, Int)]= for {
  x <- Some(5)
  // x <- None
  y <- Some(x + 6)
} yield (x, y)

// println(bailsOutOnNone)

val bailsOutOnLeft: Either[String, (Int, Int)] = for {
  x <- Right(5)
  // x <- Left("error")
  y <- Right(x + 6)
} yield (x, y)

// println(bailsOutOnLeft)

///////////////////////////////////////////////////////////////////////////////

class IO[+A](val unsafeInterpret: () => A) 

object IO {
  def effect[A](eff: => A) = new IO(() => eff)
}

object MoreFunctorInstances {
    // @ List(1, 2, 3).map(x => x + 1)
  // res0: List[Int] = List(2, 3, 4)
  implicit val ioFunctorInstance: Functor[IO] = new Functor[IO] {
    def map[A, B](fa: IO[A])(f: A => B): IO[B] = IO.effect(f(fa.unsafeInterpret()))
  }
}

object MoreMonadInstances {
  implicit val ioMonadInstance: Monad[IO] = new Monad[IO] {
    def pure[A](a: A): IO[A] = IO.effect(a)

    def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] =
      IO.effect(f(fa.unsafeInterpret()).unsafeInterpret())
  }
}

import FunctorSyntax._
import MonadSyntax._
import MoreFunctorInstances._
import MoreMonadInstances._

def putStrLn(line: String): IO[Unit] = 
  IO.effect(println(line))

val getStrLn: IO[String] = 
  IO.effect(scala.io.StdIn.readLine())

val echo: IO[Unit] = for {
  _        <- putStrLn("Please enter something to be echoed:")
  str      <- getStrLn
  _        <- putStrLn("Echoing:" + str)
} yield ()

echo.unsafeInterpret()

