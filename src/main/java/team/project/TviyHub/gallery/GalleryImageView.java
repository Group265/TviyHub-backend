package team.project.TviyHub.gallery;

import java.time.Instant;

/** Metadata projection: selecting it does not load the file bytes. */
public interface GalleryImageView {

	Long getId();

	String getCaption();

	String getContentType();

	int getSizeBytes();

	Instant getCreatedAt();
}
