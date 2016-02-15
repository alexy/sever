package io.bythebay.sever.talk

import io.bythebay.util.sever._

/**
 * Created by a on 5/26/15.
 */

case class Talk(key:       Option[String],
                id:        Int,
                tags:      List[String],
                tagsOther: List[String],
                title:     String,
                body:      String, // abstract is a keyword in Scala
                speaker:   Speaker,
                summary:   Summary) {
  override def toString: String = List(
    ("id",id),
    ("title", title),
    ("tags", tags.mkString("; ")),
    ("tagsOther", tagsOther.mkString("; ")),
    ("speaker", speaker.toString),
    ("abstract", body)
  ).map{case (field, text) => s"$field:\t$text"}
    .mkString("\n")
}

case class Headline(number: Int, speakerName: String, group: Int = 0) {
  override def toString = (number + group*100).toString + ": " + speakerName
}


case class Summary(
                    email:     String,
                    company:   Option[String],
                    role:      Option[String],
                    headline:  String,
                    title:     String,
                    body:      String,
                    tags:      List[String],
                    tagsOther: List[String],
                    twitter:   Option[String],
                    bio:       Option[String]
                  ) {
  val showTags      = tags.mkString(", ")
  val showOtherTags = tagsOther.mkString(", ")

  val companyRole = List(company, role) flatMap (identity(_)) mkString " * "

  val companyRole_nl = if (companyRole.isEmpty) "" else companyRole+"\n"

  val synopsis =

    s"""
       | ${title}
       | $companyRole_nl$showTags * ${showOtherTags}
       | -----
       | $body
         """.stripMargin
}


object Talk {

  val keys = Map(
    "timestamp"     -> "Timestamp",
    "name"          -> "Name",
    "company"       -> "Your Company",
    "role"          -> "Role",
    "email"         -> "Email address",
    "tracks"        -> "Which of the Six Conferences is this talk Suitable for?",
    "title"         -> "Talk Title",
    "link1"         -> "Talk Link 1",
    "link2"         -> "Talk Link 2",
    "github"        -> "Talk Github Repo",
    "abstract"      -> "Talk Abstract",
    "code"          -> "How much code will your talk have?",
    "data"          -> "How much data are you going to show?",
    "datasets"      -> "Talk Datasets",
    "datasetsNotes" -> "Talk Datasets, Notes",
    "length"        -> "Talk Duration",
    "notes"         -> "Notes for the organizers",
    "found"         -> "How did you learn about Data By the Bay?",
    "partner"       -> "Would your company be a partner of Data By the Bay?",
    "number"        -> "Manual",
    "keynote"       -> "Keynote"
  )

  val trackTagPrefix = ""
  val trackTags = Map(
    "Text By the Bay" -> "text",
    "Democracy By the Bay" -> "democracy",
    "AIoT By the Bay" -> "aiot",
    "Life Sciences By the Bay" -> "life",
    "Law By the Bay" -> "law",
    "UX By the Bay" -> "ux",
    "General Slot -- 2 hours on general data processing each day" -> "general"
  )

  val lengthTagPrefix = ""
  val lengthTags = Map(
    "40 minutes" -> "40",
    "20 minutes" -> "20"
  )

  val dataTagPrefix = "data-"
  val dataTags = Map(
    "Working with public data sets, linked above" -> "linked",
    "Showing proprietary data, lots of it" -> "proprietary",
    "Showing lots of data summaries" -> "summaries",
    "Generally alluding to &quot;data in the cloud&quot;" -> "cloud"
  )

  val codeTagPrefix = "code-"
  val codeTags = Map(
    "Live coding.  The best!" -> "live",
    "Showing code in an IDE/on Github" -> "github",
    "Code on slides" -> "slides",
    "Making air curly braces with hand waves" -> "air"
  )

  val duplicates = List(List(7,8),List(15,128),List(24,25),List(37,38),List(102,117))

  def readFromTSV(filename: String): List[Talk] = {
    scala.io.Source.fromFile(filename).getLines().toList match {
      case schemaRow :: lines =>

        val lineOffset = 2 // header line and 0-based zipWithIndex
        val dropLines = duplicates.map(_.dropRight(1)).reduce(_++_).map(_-lineOffset).toSet
        val uniques = lines.zipWithIndex.filterNot{ case (_,number) => dropLines.contains(number) }

        val dropped = lines.zipWithIndex.filter{ case (_,number) => dropLines.contains(number) }
        dropped foreach { case (line, number) => println(s"DROP LINE ${number+lineOffset}: $line") }

        println(schemaRow)

        val schema: Map[String, Int] = schemaRow.split("\t").zipWithIndex.toMap

        def position(key: String): Int = schema(keys(key))

        try {
          //          val keyPos   = position("key")
          val namePos    = position("name")
          val emailPos   = position("email")

          //          val optCompanyPos = tryKeys(schema)(List("Company and role", "Current company and role")) // optional in key, value, field
          val companyPos = position("company")
          val rolePos    = position("role")

          val titlePos   = position("title")
          val bodyPos    = position("abstract")
          val lengthPos  = position("length")

          //          val optTwitterPos = schema("Speaker's Twitter handle")
          //          val bioPos        = schema("Speaker Bio")
          //          val optPhotoPos   = schema("Speaker Photo")

          val tracksPos  = position("tracks")
          val dataPos    = position("data")
          val codePos    = position("code")
          val numberPos  = position("number")
          val keynotePos = position("keynote")

          uniques flatMap { case (line, i) =>
//            println(line); System.out.flush()
            try {
              val fields: List[String] = line.split("\t").toList.map(xml.Utility.escape)
              val f:  Int => String         = fieldOrEmpty1(fields)
              val fo: Int => Option[String] = fieldOrNone1(fields)

              //              val optCompany = for {pos <- optCompanyPos; s <- fo(pos)} yield s

              val speaker =
                Speaker(
                  name       = f(namePos),
                  email      = f(emailPos),
                  companyOpt = fo(companyPos),
                  roleOpt    = fo(rolePos)
                  //                twitterOpt = fo(optTwitterPos), bio = f(bioPos), photoOpt = fo(optPhotoPos)
                )
              println("role: " + speaker.roleOpt.getOrElse("<no role>"))

              val keynoteOpt = fo(keynotePos)

              val (tags, tagsOther) = {
                val (t, ta) = resolveTags(trackTags,  trackTagPrefix)(f(tracksPos))
//                println(s"tracks: " + t)
                val (l, la) = resolveTags(lengthTags, lengthTagPrefix)(f(lengthPos))
                val (d, da) = resolveTags(dataTags,   dataTagPrefix)(f(dataPos))
                val (c, ca) = resolveTags(codeTags,   codeTagPrefix)(f(codePos))

                (t ++ l ++ d ++ c ++ keynoteOpt.toList,
                  List(ta, la, da, ca) flatMap (identity(_)))
              }

              val number = fo(numberPos).map{ x =>
                val n = x.toInt
                println(s"MANUAL number: $n")
                n }.getOrElse(i)
              val title = f(titlePos)
              val body  = f(bodyPos)

              val headline = Headline(i+1, speaker.name)

              val summary =
                Summary(
                  email     =speaker.email,
                  twitter   =speaker.twitterOpt,
                  company   =speaker.companyOpt,
                  role      =speaker.roleOpt,
                  bio       =speaker.bioOpt,
                  headline  =headline.toString,
                  tags      =tags,
                  tagsOther =tagsOther,
                  title     =title,
                  body      =body
                )

              Some(
                Talk(
                  key       = None, //fo(key),
                  id        = number,
                  tags      = tags,
                  tagsOther = tagsOther,
                  title     = title,
                  body      = body,
                  speaker   = speaker,
                  summary   = summary
                )
              )
            } catch {
              case e: NoSuchElementException =>
                println("missing element: " + e)
                None
            }
          }
        } catch {
          case e: NoSuchElementException =>
            println("missing field: " + e)
            List() // or we can simply return a Try
        }

      case _ => println("there seems to be not enough data in your table!")
        List()
    }
  }
}

object ShowTalks {
  def main(args: Array[String]): Unit = {
    val inputFile = if (args.length>0) args(0) else "dbtb.tsv"

    println("showing talks from " + inputFile)
    val talks = Talk.readFromTSV(inputFile)

    talks foreach { case t =>
        val tags = t.tags.mkString(";")
        val tagsOther = t.tagsOther.mkString(";")
        println(s"tags: $tags ... other: $tagsOther")
    }
  }
}