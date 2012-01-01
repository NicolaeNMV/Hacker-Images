package utils

import play.api.libs.concurrent._

object `package` {
  implicit def listToPromiseSequencableList[A](list: List[Promise[A]]) = new PromiseSequencableList[A](list)
}

class PromiseSequencableList[A](list: List[Promise[A]]) {
  def sequence(): Promise[List[A]] = list.foldLeft(Promise.pure(List[A]()))((s,p) => s.flatMap( s => p.map(a => s :+ a)))
}

