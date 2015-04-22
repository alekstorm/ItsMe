package models

import java.util.UUID

import Driver.simple._

case class User(id: UUID, name: String, email: String, password: String, gender: String, pin: String)

class Users(tag: Tag) extends Table[User](tag, "users") {
  def id = column[UUID]("id")
  def name = column[String]("name")
  def email = column[String]("email")
  def password = column[String]("password")
  def gender = column[String]("gender")
  def pin = column[String]("pin")
  def * = (id, name, email, password, gender, pin) <> (User.tupled, User.unapply _)
}

object Users {
  val query = TableQuery[Users]

  def getByEmail(email: String)(implicit s: Session): Option[User] = {
    query.filter(_.email === email).firstOption
  }

  def getByLogin(email: String, password: String)(implicit s: Session): Option[User] = {
    query.filter(u => u.email === email && u.password === password).firstOption
  }

  def create(name: String, email: String, password: String, gender: String, pin: String)(implicit s: Session): UUID = {
    val id = UUID.randomUUID()
    query.insert(User(UUID.randomUUID(), name, email, password, gender, pin))
    id
  }
}
