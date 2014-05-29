//inner class
class ReturnSeveralOutput2 {
  def foo(i: Int): Int = {
    /*start*/

    val x = i
    if (true) return x
    val y = "a"
    var z = 1
    val zz = "1"
    /*end*/
    println(x + y + z + zz)
    i
  }
}
/*
//inner class
class ReturnSeveralOutput2 {
  def foo(i: Int): Int = {
    /*start*/

    val testMethodNameResult: TestMethodNameResult = testMethodName(i) match {
      case Left(toReturn) => return toReturn
      case Right(result) => result
    }
    val x: Int = testMethodNameResult.x
    val y: String = testMethodNameResult.y
    var z: Int = testMethodNameResult.z
    val zz: String = testMethodNameResult.zz

    /*end*/
    println(x + y + z + zz)
    i
  }

  class TestMethodNameResult(val x: Int, val y: String, val z: Int, val zz: String)

  def testMethodName(i: Int): Either[Int, TestMethodNameResult] = {
    val x = i
    if (true) return Left(x)
    val y = "a"
    var z = 1
    val zz = "1"
    Right(new TestMethodNameResult(x, y, z, zz))
  }
}
*/