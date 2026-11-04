package team.project.TviyHub.gallery;

import java.util.Optional;

/** Image formats the gallery accepts, recognized by the first bytes of the file. */
public enum ImageType {
	JPEG("image/jpeg"),
	PNG("image/png"),
	WEBP("image/webp");

	private final String contentType;

	ImageType(String contentType) {
		this.contentType = contentType;
	}

	public String contentType() {
		return contentType;
	}

	public static Optional<ImageType> detect(byte[] data) {
		if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
			return Optional.of(JPEG);
		}
		if (startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
			return Optional.of(PNG);
		}
		// RIFF, 4 bytes of length, WEBP
		if (data.length >= 12 && startsWith(data, 'R', 'I', 'F', 'F')
				&& data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] data, int... prefix) {
		if (data.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if ((data[i] & 0xFF) != prefix[i]) {
				return false;
			}
		}
		return true;
	}
}
