package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupTest {
    private fun clean(text: String) = Cleanup.apply(text).text
    private fun unchanged(text: String) {
        val result = Cleanup.apply(text)
        assertEquals(text, result.text)
        assertTrue("expected no changes but got ${result.changes}", result.changes.isEmpty())
    }

    // ---- The table from the spec ----

    @Test fun monthYearRangeInBrackets() = assertEquals(
        "He served from April 1964 to May 1964 as mayor.",
        clean("He served (April 1964 – May 1964) as mayor."),
    )

    @Test fun yearRange() = assertEquals("It ran 1964 to 1968.", clean("It ran 1964–1968."))

    @Test fun dayRange() = assertEquals("On 12 to 15 March 1966 it rained.", clean("On 12–15 March 1966 it rained."))

    @Test fun lifespanInBrackets() = assertEquals(
        "John Smith, 1920 to 1987, was a writer.",
        clean("John Smith (1920–1987) was a writer."),
    )

    @Test fun born() = assertEquals("Jane Doe, born 1950, is a writer.", clean("Jane Doe (born 1950) is a writer."))

    @Test fun bornAbbreviated() = assertEquals("Jane Doe, born 1950, is a writer.", clean("Jane Doe (b. 1950) is a writer."))

    @Test fun died() = assertEquals("Her father, died 1987, was a baker.", clean("Her father (d. 1987) was a baker."))

    @Test fun reigned() = assertEquals(
        "Henry VIII, reigned 1509 to 1547, had six wives.",
        clean("Henry VIII (r. 1509–1547) had six wives."),
    )

    @Test fun circa() = assertEquals("Built circa 1200 and rebuilt circa 1350.", clean("Built c. 1200 and rebuilt ca. 1350."))

    @Test fun citationMarkers() = assertEquals(
        "The sky is blue. Grass is green. Water is wet.",
        clean("The sky is blue.[12] Grass is green.[a] Water is wet.[citation needed]"),
    )

    @Test fun pronunciationGuide() = assertEquals(
        "The Alamo is a mission.",
        clean("The Alamo (/ˈæləmoʊ/ AL-ə-moh) is a mission."),
    )

    @Test fun unitWithConversion() = assertEquals("It is 5 kilometers away.", clean("It is 5 km (3.1 mi) away."))

    @Test fun unitRange() = assertEquals("About 10 to 15 kilometers wide.", clean("About 10–15 km wide."))

    @Test fun number() = assertEquals("Symphony number 3 in D.", clean("Symphony No. 3 in D."))

    @Test fun forExampleAndThatIs() = assertEquals(
        "Fruit, for example apples, that is pomes.",
        clean("Fruit, e.g. apples, i.e. pomes."),
    )

    @Test fun versus() = assertEquals("Brown versus Board of Education", clean("Brown vs. Board of Education"))

    @Test fun approximately() = assertEquals("It weighs approximately 5 kilograms.", clean("It weighs approx. 5 kg."))

    @Test fun ampersandBetweenWords() = assertEquals("Smith and Jones", clean("Smith & Jones"))

    // ---- Abbreviations in brackets ----

    @Test fun abbreviationFromInitials() = assertEquals(
        "The World Health Organization, abbreviated to WHO, is an agency.",
        clean("The World Health Organization (WHO) is an agency."),
    )

    @Test fun abbreviationSkippingSmallWords() = assertEquals(
        "The National Aeronautics and Space Administration, abbreviated to NASA, runs it.",
        clean("The National Aeronautics and Space Administration (NASA) runs it."),
    )

    @Test fun abbreviationFromInsideAWord() = assertEquals(
        "Deoxyribonucleic acid, abbreviated to DNA, is a polymer.",
        clean("Deoxyribonucleic acid (DNA) is a polymer."),
    )

    @Test fun pluralAbbreviation() = assertEquals(
        "Many non-governmental organizations, abbreviated to NGOs, took part.",
        clean("Many non-governmental organizations (NGOs) took part."),
    )

    @Test fun abbreviationBeforeFullStop() = assertEquals(
        "It joined the European Union, abbreviated to EU.",
        clean("It joined the European Union (EU)."),
    )

    @Test fun abbreviationAfterPronunciationIsRemoved() = assertEquals(
        "The North Atlantic Treaty Organization, abbreviated to NATO, was founded in 1949.",
        clean("The North Atlantic Treaty Organization (NATO; /ˈneɪtoʊ/) was founded in 1949."),
    )

    @Test fun capitalsThatAreNotAnAbbreviationOfTheWordsBefore() = unchanged("He was born in London (UK) and left.")

    @Test fun ratingInBrackets() = unchanged("The film (PG) did well.")

    @Test fun romanNumeralInBrackets() = unchanged("The Vickers Mark (VI) tank was light.")

    @Test fun ordinaryWordInBrackets() = unchanged("The Organization of States (see below) met.")

    // ---- Left unchanged because they are ambiguous ----

    @Test fun ampersandInsideNames() = unchanged("R&D at AT&T")

    @Test fun scores() = unchanged("They won 3–1 in the final.")

    @Test fun pageRanges() = unchanged("See pages 12–15 of the report.")

    @Test fun saintOrStreet() = unchanged("St. John lived on Main St. for years.")

    @Test fun romanNumerals() = unchanged("Henry VIII fought in World War II with III Corps.")

    @Test fun otherBrackets() = unchanged("The film (released late) did well.")

    @Test fun theWordIn() = unchanged("He scored 5 in the final and 3 in 1964.")

    @Test fun millionsOfDollars() = unchanged("It cost $5 m to build.")

    @Test fun speedPerSecond() = unchanged("It moves at 5 m/s.")

    // ---- Wikipedia plain-text leftovers ----

    @Test fun leftoverSemicolonWithLifespan() = assertEquals(
        "Alan Turing, 23 June 1912 to 7 June 1954, was a mathematician.",
        clean("Alan Turing (; 23 June 1912 – 7 June 1954) was a mathematician."),
    )

    @Test fun pronunciationThenLifespan() = assertEquals(
        "Alan Turing, 23 June 1912 to 7 June 1954, was a mathematician.",
        clean("Alan Turing (/ˈtjʊərɪŋ/; 23 June 1912 – 7 June 1954) was a mathematician."),
    )

    @Test fun emptyBrackets() = assertEquals("The Alamo is a mission.", clean("The Alamo () is a mission."))

    @Test fun mathsMarkup() = assertEquals(
        "The formula is famous.",
        clean("The formula {\\displaystyle E=mc^{2}} is famous."),
    )

    @Test fun nonBreakingSpaceBeforeUnit() = assertEquals("It is 5 kilometers away.", clean("It is 5 km away."))

    // ---- More dates ----

    @Test fun shortYearRange() = assertEquals("The 1914 to 18 war.", clean("The 1914–18 war."))

    @Test fun yearToPresent() = assertEquals("Mayor, 2001 to present.", clean("Mayor (2001–present)."))

    @Test fun americanDayRange() = assertEquals("On March 12 to 15, 1966.", clean("On March 12–15, 1966."))

    @Test fun monthRange() = assertEquals("From June to July 1954.", clean("From June–July 1954."))

    @Test fun bornWithFullDate() = assertEquals(
        "Jane Doe, born 4 August 1961, is a writer.",
        clean("Jane Doe (born 4 August 1961) is a writer."),
    )

    @Test fun lifespanBeforeFullStop() = assertEquals(
        "It was written by John Smith, 1920 to 1987.",
        clean("It was written by John Smith (1920–1987)."),
    )

    @Test fun isoDatesAreLeftAlone() = unchanged("Recorded on 2001-09-11 at noon.")

    // ---- More units ----

    @Test fun singularUnit() = assertEquals("About 1 kilometer long.", clean("About 1 km long."))

    @Test fun feetAndInches() = assertEquals("He is 5 feet 10 inches tall.", clean("He is 5 ft 10 in (178 cm) tall."))

    @Test fun squareKilometers() = assertEquals(
        "The park covers 1,200 square kilometers.",
        clean("The park covers 1,200 km2 (460 sq mi)."),
    )

    @Test fun temperature() = assertEquals(
        "It fell to minus 5 degrees Celsius overnight.",
        clean("It fell to −5 °C (23 °F) overnight."),
    )

    @Test fun negativeConversion() = assertEquals(
        "Lows of minus 36 degrees Celsius are common.",
        clean("Lows of −36 °C (−33 °F) are common."),
    )

    @Test fun speed() = assertEquals("Winds of 120 kilometers per hour.", clean("Winds of 120 km/h (75 mph)."))

    @Test fun bracketAfterUnitThatIsNotAConversion() = assertEquals(
        "It is 5 kilometers (as the crow flies) away.",
        clean("It is 5 km (as the crow flies) away."),
    )

    @Test fun changesAreRecorded() {
        val changes = Cleanup.apply("It ran 1964–1968 for 5 km (3.1 mi).").changes
        assertEquals(
            listOf(
                Change("Date range", "1964–1968", "1964 to 1968"),
                Change("Unit", "5 km (3.1 mi)", "5 kilometers"),
            ),
            changes,
        )
    }
}
