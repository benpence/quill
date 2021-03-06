package io.getquill.context.spark

import io.getquill.Spec
import org.apache.spark.sql.Dataset
import org.scalatest.Matchers._

case class Contact(firstName: String, lastName: String, age: Int, addressFk: Int, extraInfo: String)
case class Address(id: Int, street: String, zip: Int, otherExtraInfo: String)
case class AddressableContact(firstName: String, lastName: String, age: Int, street: String, zip: Int)

case class ContactSimplified(firstName: String, lastNameRenamed: String, firstReverse: String)
case class ContactSimplifiedMapped(firstNameMapped: String, lastNameMapped: String, firstReverseMapped: String)

case class ContactAndAddress(c: Contact, a: Address)
case class Note(owner: String, content: String)

class CaseClassQuerySpec extends Spec {

  val context = io.getquill.context.sql.testContext

  val expectedData = Seq(
    ContactSimplified("Alex", "Jones", "Alex".reverse),
    ContactSimplified("Bert", "James", "Bert".reverse),
    ContactSimplified("Cora", "Jasper", "Cora".reverse)
  )

  import testContext._
  import sqlContext.implicits._

  val peopleList = Seq(
    Contact("Alex", "Jones", 60, 2, "foo"),
    Contact("Bert", "James", 55, 3, "bar"),
    Contact("Cora", "Jasper", 33, 3, "baz")
  )
  val peopleEntries = liftQuery(peopleList.toDS())

  val addressList = Seq(
    Address(1, "123 Fake Street", 11234, "something"),
    Address(2, "456 Old Street", 45678, "something else"),
    Address(3, "789 New Street", 89010, "another thing")
  )
  val addressEntries = liftQuery(addressList.toDS())

  val noteList = Seq(
    Note("Alex", "Foo"),
    Note("Alex", "Bar"),
    Note("Bert", "Baz"),
    Note("Bert", "Taz")
  )
  val noteEntries = liftQuery(noteList.toDS())

  val reverse = quote {
    (str: String) => infix"reverse(${str})".as[String]
  }

  "Simple Join" in {
    val q = quote {
      for {
        p <- peopleEntries
        a <- addressEntries if p.addressFk == a.id
      } yield {
        new AddressableContact(p.firstName, p.lastName, p.age, a.street, a.zip)
      }
    }

    testContext.run(q).collect() should contain theSameElementsAs Seq(
      AddressableContact("Alex", "Jones", 60, "456 Old Street", 45678),
      AddressableContact("Bert", "James", 55, "789 New Street", 89010),
      AddressableContact("Cora", "Jasper", 33, "789 New Street", 89010)
    )
  }

  "Simple Join, Ad-Hoc Case Class, Filtered Union" in {
    val q = quote {
      for {
        p <- (peopleEntries.filter(_.age >= 60)) ++ (peopleEntries.filter(_.age < 60))
        a <- addressEntries if p.addressFk == a.id
      } yield {
        new AddressableContact(p.firstName, p.lastName, p.age, a.street, a.zip)
      }
    }

    testContext.run(q).collect() should contain theSameElementsAs Seq(
      AddressableContact("Alex", "Jones", 60, "456 Old Street", 45678),
      AddressableContact("Bert", "James", 55, "789 New Street", 89010),
      AddressableContact("Cora", "Jasper", 33, "789 New Street", 89010)
    )
  }

  "Simple Join, Ad-Hoc Case Class, Filtered Union Distinct" in {
    val q = quote {
      (for {
        // returns a duplicate record that should be deduped
        p <- (peopleEntries.filter(_.age >= 60)) ++ (peopleEntries.filter(_.age <= 60))
        a <- addressEntries if p.addressFk == a.id
      } yield {
        new AddressableContact(p.firstName, p.lastName, p.age, a.street, a.zip)
      }).distinct
    }

    testContext.run(q).collect() should contain theSameElementsAs Seq(
      AddressableContact("Alex", "Jones", 60, "456 Old Street", 45678),
      AddressableContact("Bert", "James", 55, "789 New Street", 89010),
      AddressableContact("Cora", "Jasper", 33, "789 New Street", 89010)
    )
  }

  "Simple Join Nested Objects Explicit and Ad-Hoc Case Class" in {
    val q = quote {
      for {
        p <- peopleEntries
        a <- addressEntries if p.addressFk == a.id
      } yield (p, a, AddressableContact(p.firstName, p.lastName, p.age, a.street, a.zip))
    }
    testContext.run(q).collect() should contain theSameElementsAs Seq(
      (Contact("Alex", "Jones", 60, 2, "foo"), Address(2, "456 Old Street", 45678, "something else"), AddressableContact("Alex", "Jones", 60, "456 Old Street", 45678)),
      (Contact("Bert", "James", 55, 3, "bar"), Address(3, "789 New Street", 89010, "another thing"), AddressableContact("Bert", "James", 55, "789 New Street", 89010)),
      (Contact("Cora", "Jasper", 33, 3, "baz"), Address(3, "789 New Street", 89010, "another thing"), AddressableContact("Cora", "Jasper", 33, "789 New Street", 89010))
    )
  }

  "Simple Join Nested Objects" in {
    val q = quote {
      peopleEntries.join(addressEntries).on(_.addressFk == _.id)
    }
    testContext.run(q).collect() should contain theSameElementsAs Seq(
      (Contact("Alex", "Jones", 60, 2, "foo"), Address(2, "456 Old Street", 45678, "something else")),
      (Contact("Bert", "James", 55, 3, "bar"), Address(3, "789 New Street", 89010, "another thing")),
      (Contact("Cora", "Jasper", 33, 3, "baz"), Address(3, "789 New Street", 89010, "another thing"))
    )
  }

  "Simple Join Nested Object" in {
    val q = quote {
      for {
        p <- peopleEntries
        a <- addressEntries if p.addressFk == a.id
      } yield (p, a)
    }
  }

  "Simple Join Nested Objects Explicit Distinct" in {
    val q = quote {
      (for {
        p <- peopleEntries
        a <- addressEntries if p.addressFk == a.id
      } yield (p, a)).distinct
    }
    testContext.run(q).show()
    testContext.run(q).collect() should contain theSameElementsAs Seq(
      (Contact("Alex", "Jones", 60, 2, "foo"), Address(2, "456 Old Street", 45678, "something else")),
      (Contact("Bert", "James", 55, 3, "bar"), Address(3, "789 New Street", 89010, "another thing")),
      (Contact("Cora", "Jasper", 33, 3, "baz"), Address(3, "789 New Street", 89010, "another thing"))
    )
  }

  "Simple Join Nested Objects Explicit Union Distinct" in {
    val q = quote {
      (for {
        p <- (peopleEntries ++ peopleEntries)
        a <- addressEntries if p.addressFk == a.id
      } yield (p, a)).distinct
    }
    testContext.run(q).collect() should contain theSameElementsAs Seq(
      (Contact("Alex", "Jones", 60, 2, "foo"), Address(2, "456 Old Street", 45678, "something else")),
      (Contact("Bert", "James", 55, 3, "bar"), Address(3, "789 New Street", 89010, "another thing")),
      (Contact("Cora", "Jasper", 33, 3, "baz"), Address(3, "789 New Street", 89010, "another thing"))
    )
  }

  "Simple Join Nested Objects Explicit Union Distinct with Filters" in {
    val q = quote {
      (for {
        p <- (peopleEntries.filter(_.age == 55) ++ peopleEntries.filter(_.age == 33) ++ peopleEntries.filter(_.age <= 33))
        a <- addressEntries if p.addressFk == a.id
      } yield (p, a)).distinct
    }
    testContext.run(q).collect() should contain theSameElementsAs Seq(
      (Contact("Bert", "James", 55, 3, "bar"), Address(3, "789 New Street", 89010, "another thing")),
      (Contact("Cora", "Jasper", 33, 3, "baz"), Address(3, "789 New Street", 89010, "another thing"))
    )
  }

  "Three Level Join with Two Nested Distincts and Nested Objects" in {
    val peopleAndIds = quote {
      for {
        id <- noteEntries
        person <- peopleEntries if person.firstName == id.owner
      } yield (person)
    }

    val q = quote {
      (for {
        p <- peopleAndIds.distinct
        a <- addressEntries if p.addressFk == a.id
      } yield (p, a)).distinct
    }

    testContext.run(q).collect() should contain theSameElementsAs Seq(
      (Contact("Bert", "James", 55, 3, "bar"), Address(3, "789 New Street", 89010, "another thing")),
      (Contact("Alex", "Jones", 60, 2, "foo"), Address(2, "456 Old Street", 45678, "something else"))
    )
  }

  "Simple Join Nested Objects Case Class" in {
    val q = quote {
      for {
        p <- peopleEntries
        a <- addressEntries if p.addressFk == a.id
      } yield ContactAndAddress(p, a)
    }
    testContext.run(q).collect() should contain theSameElementsAs Seq(
      ContactAndAddress(Contact("Alex", "Jones", 60, 2, "foo"), Address(2, "456 Old Street", 45678, "something else")),
      ContactAndAddress(Contact("Bert", "James", 55, 3, "bar"), Address(3, "789 New Street", 89010, "another thing")),
      ContactAndAddress(Contact("Cora", "Jasper", 33, 3, "baz"), Address(3, "789 New Street", 89010, "another thing"))
    )
  }

  "Simple Left Join Optional Objects" in {
    val q = quote {
      peopleEntries.leftJoin(addressEntries).on(_.addressFk == _.id)
    }
    testContext.run(q).collect() should contain theSameElementsAs Seq(
      (Contact("Alex", "Jones", 60, 2, "foo"), Some(Address(2, "456 Old Street", 45678, "something else"))),
      (Contact("Bert", "James", 55, 3, "bar"), Some(Address(3, "789 New Street", 89010, "another thing"))),
      (Contact("Cora", "Jasper", 33, 3, "baz"), Some(Address(3, "789 New Street", 89010, "another thing")))
    )
  }

  "Simple Join - External Map" in {
    val q = quote {
      for {
        p <- peopleEntries
        a <- addressEntries if p.addressFk == a.id
      } yield {
        AddressableContact(p.firstName, p.lastName, p.age, a.street, a.zip)
      }
    }

    val dataset: Dataset[AddressableContact] = testContext.run(q)
    val mapped = dataset.map(ac => ContactSimplified(ac.firstName, ac.lastName, ac.firstName.reverse))

    mapped.collect() should contain theSameElementsAs expectedData
  }

  "Simple Select" in {
    val q = quote {
      for {
        p <- peopleEntries
      } yield ContactSimplified(p.firstName, p.lastName, reverse(p.firstName))
    }
    testContext.run(q).collect() should contain theSameElementsAs expectedData
  }

  "Two Level Select" in {
    val q = quote {
      for {
        p <- peopleEntries
      } yield ContactSimplified(p.firstName, p.lastName, reverse(p.firstName))
    }

    val q2 = quote {
      for {
        p <- q
      } yield ContactSimplified(p.firstName, p.lastNameRenamed, reverse(p.firstName))
    }
    testContext.run(q2).collect() should contain theSameElementsAs expectedData
  }

  "Two Level Select - Filtered First Part" in {
    val q = quote {
      for {
        p <- peopleEntries if (p.firstName == "Bert")
      } yield ContactSimplified(p.firstName, p.lastName, reverse(p.firstName))
    }

    val q2 = quote {
      for {
        p <- q
      } yield ContactSimplified(p.firstName, p.lastNameRenamed, reverse(p.firstName))
    }
    testContext.run(q2).collect() should contain theSameElementsAs expectedData.filter(_.firstName == "Bert")
  }

  "Two Level Select - Filtered Second Part" in {
    val q = quote {
      for {
        p <- peopleEntries
      } yield ContactSimplified(p.firstName, p.lastName, reverse(p.firstName))
    }

    val q2 = quote {
      for {
        p <- q if (p.lastNameRenamed == "James")
      } yield ContactSimplified(p.firstName, p.lastNameRenamed, reverse(p.firstName))
    }
    testContext.run(q2).collect() should contain theSameElementsAs expectedData.filter(_.firstName == "Bert")
  }

  "Two Level Select - Filtered First and Second Part" in {
    val q = quote {
      for {
        p <- peopleEntries if (p.firstName == "Bert" || p.firstName == "Alex")
      } yield ContactSimplified(p.firstName, p.lastName, reverse(p.firstName))
    }

    val q2 = quote {
      for {
        p <- q if (p.lastNameRenamed == "James")
      } yield ContactSimplified(p.firstName, p.lastNameRenamed, reverse(p.firstName))
    }
    testContext.run(q2).collect() should contain theSameElementsAs expectedData.filter(_.firstName == "Bert")
  }

  "Two Level Select Tuple" in {
    val q = quote {
      for {
        p <- peopleEntries
      } yield (p.firstName, p.lastName, reverse(p.firstName))
    }

    val q2 = quote {
      for {
        p <- q
      } yield ContactSimplifiedMapped(p._1, p._2, reverse(p._1))
    }

    testContext.run(q2).collect() should contain theSameElementsAs expectedData.map(
      c => ContactSimplifiedMapped(c.firstName, c.lastNameRenamed, c.firstReverse)
    )
  }

  "Nested Class Right Join" in {
    val q = quote {
      for {
        a <- addressEntries
        p <- peopleEntries if p.addressFk == a.id
      } yield (a, p)
    }

    val expected = addressList.flatMap(a => peopleList.filter(_.addressFk == a.id).map(p => (a, p)))
    testContext.run(q).collect() should contain theSameElementsAs expected
  }
}
