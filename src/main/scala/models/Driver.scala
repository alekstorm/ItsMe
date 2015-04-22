package models

import com.github.tminglei.slickpg._
import slick.driver.PostgresDriver

/*object Driver extends PostgresDriver
                 with PgArraySupport
                 with PgDateSupport
                 with PgRangeSupport
                 with PgSearchSupport {
  override lazy val Implicit = new Implicits
                     with ArrayImplicits
                     with DateTimeImplicits
                     with RangeImplicits
                     with SearchImplicits

  override object simple extends SimpleQL
                   with SearchAssistants
}*/

trait Driver extends PostgresDriver
                          with PgArraySupport
                          with PgDateSupport
                          with PgRangeSupport
                          with PgSearchSupport {

  override lazy val Implicit = new ImplicitsPlus {}
  override val simple = new SimpleQLPlus {}

  //////
  trait ImplicitsPlus extends Implicits
                        with ArrayImplicits
                        with DateTimeImplicits
                        with RangeImplicits
                        with SearchImplicits

  trait SimpleQLPlus extends SimpleQL
                        with ImplicitsPlus
                        with SearchAssistants
}

object Driver extends Driver
