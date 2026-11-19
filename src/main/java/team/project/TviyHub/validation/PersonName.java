package team.project.TviyHub.validation;

/** Rule for a person's name (first and last name in one field). */
public final class PersonName {

	private static final String LETTER = "[A-Za-z\u0410-\u044F\u0401\u0451\u0406\u0456\u0407\u0457\u0404\u0454\u0490\u0491]";

	/**
	 * Only English, Ukrainian or Russian letters. Words may be separated by a single space, hyphen or
	 * apostrophe (O'Neil, Анна-Марія, Дар'я); no digits or other symbols.
	 */
	public static final String REGEX = "^" + LETTER + "+(?:[ '\u2019\u02BC-]" + LETTER + "+)*$";

	public static final String MESSAGE = "must contain only English, Ukrainian or Russian letters (words may be separated by a space, hyphen or apostrophe)";

	private PersonName() {
	}
}
