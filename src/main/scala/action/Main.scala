package action

import java.sql.ResultSet
import javax.sql.DataSource

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.zaxxer.hikari.HikariDataSource

/**
 * Author: kui.dai
 * Date: 2016/1/18.
 */
trait Resources {
  def using[A <: AutoCloseable, B](resource: A)(handler: A ⇒ B): B = {
    try {
      handler(resource)
    } finally {
      resource.close()
    }
  }
}

trait IdEntity {
  val id: Long
}

case class User(id: Long, name: String, sex: Int, age: Int, mobile: String) extends IdEntity {
  //Not recommend
  def this(rs: ResultSet) {
    this(id = rs.getLong("id"),
      name = rs.getString("name"),
      sex = rs.getInt("sex"),
      age = rs.getInt("age"),
      mobile = rs.getString("mobile"))
  }
}

object User {
  def apply(rs: ResultSet): User = {
    User(
      id = rs.getLong("id"),
      name = rs.getString("name"),
      sex = rs.getInt("sex"),
      age = rs.getInt("age"),
      mobile = rs.getString("mobile")
    )
  }
}

trait JdbcTemplate[T <: IdEntity] extends Resources {
  type BeanMapper = ResultSet ⇒ T
  val log: LoggingAdapter
  val tableName: String
  val beanMapper: BeanMapper

  def find(id: String)(implicit dataSource: DataSource): Iterator[T] = query(s"select * from $tableName where id=$id", beanMapper)

  def query[A](sql: String, mapper: ResultSet ⇒ A)(implicit dataSource: DataSource): Iterator[A] =
    using(dataSource.getConnection) {
      conn ⇒ using(conn.prepareStatement(sql)) {
        ps ⇒ using(ps.executeQuery()) {
          rs ⇒ new Iterator[A] {
            override def hasNext: Boolean = rs.next()

            override def next(): A = mapper(rs)
          }
        }
      }
    }
}

trait Pager[A <: IdEntity] {
  template: JdbcTemplate[A] ⇒
  def pageQuery(start: Int, pageSize: Int, pageSql: (Int, Int) ⇒ String)(implicit dataSource: DataSource): Iterator[A] =
    template.query(pageSql(start, pageSize), beanMapper)
}

trait UserService extends JdbcTemplate[User] with Pager[User] {
  override val tableName: String = "user"
  override val beanMapper: BeanMapper = User(_)
}

class UserServiceImpl()(implicit val log: LoggingAdapter) extends UserService

object UserService {
  def apply()(implicit log: LoggingAdapter) = new UserServiceImpl()
}

trait JsonProtocol {
  val objectMapper = new ObjectMapper() with ScalaObjectMapper registerModule DefaultScalaModule

  implicit def userToCsv(user: User): String = objectMapper.writeValueAsString(user)
}

object Main extends App with JsonProtocol {
  implicit val system = ActorSystem()
  implicit val mater = ActorMaterializer()
  implicit val logAdapter = system.log
  implicit val dataSource = new HikariDataSource()
  dataSource.setJdbcUrl("jdbc:mysql://172.18.2.154:3306/test")
  dataSource.setUsername("admin")
  dataSource.setPassword("admin123")
  val userService = UserService()
  val data = Iterator.from(1, 10).flatMap(userService.pageQuery(_, 100, (pageNum, pageSize) ⇒ s"select * from user limit $pageNum,$pageSize"))
  Source(() ⇒ data).runForeach(println)
}