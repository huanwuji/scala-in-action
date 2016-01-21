# scala-in-action
## function

def test = println("aa")
x.map(_+_)
x.map(x => x+x)
x.map{x => x+x}
x.map {
  case x:Int => 1
  case _ => 1
}

## curring
type Handler = Int => Long
def test(a:Int)(handler:Int => Long)

case class User(id:Int, name:String)

##trait
trait Service {
    def find(id:Int):User
 }
class ServiceImpl extend Service

##object
object {
    def apply() = new ServiceImpl
}

## pattern matching
val x = List(1,2)
x match {
   case x::_ =>
   case _
}

##Variances
`trait Animal[+T]`

## type bounds
`def test[A <: Any](a:A)`
`def test[A >: Any](a:A)`

##compound types
val objectMapper = new ObjectMapper() with ScalaObjectMapper

##typed self

##implicit