package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTest {
    private fun check(expected: String, description: String, title: String = "") =
        assertEquals("\"$description\"", expected, Category.of(description, title))

    @Test fun videoGame() = check("game", "2024 video game")
    @Test fun vietnamWar() = check("war", "1955–1975 conflict in Vietnam")
    @Test fun worldWar() = check("war", "1939–1945 global conflict between two coalitions")
    @Test fun person() = check("person", "English mathematician and computer scientist")
    @Test fun filmDirectorIsAPerson() = check("person", "American film director")
    @Test fun film() = check("film", "2019 American superhero film")
    @Test fun songByASinger() = check("music", "2019 song by American singer")
    @Test fun band() = check("music", "English rock band")
    @Test fun novel() = check("book", "1949 novel by George Orwell")
    @Test fun footballClub() = check("sport", "Association football club in Manchester, England")
    @Test fun bird() = check("animal", "Species of bird")
    @Test fun planet() = check("space", "Fourth planet from the Sun")
    @Test fun disease() = check("science", "Infectious disease caused by a virus")
    @Test fun mountain() = check("nature", "Highest mountain on Earth")
    @Test fun city() = check("place", "Capital and largest city of France")
    @Test fun empire() = check("history", "Ancient Roman empire")
    @Test fun castle() = check("building", "Medieval castle in Scotland")
    @Test fun noDescriptionUsesTheTitle() = check("war", "", "Vietnam War")
    @Test fun battleTitle() = check("war", "", "Battle of Hastings")
    @Test fun unknown() = check("article", "", "Xylophone")
}
