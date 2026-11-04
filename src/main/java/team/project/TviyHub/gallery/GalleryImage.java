package team.project.TviyHub.gallery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A photo in the hub gallery. The file bytes are stored in the database. */
@Entity
@Table(name = "gallery_images")
@Getter
@Setter
@NoArgsConstructor
public class GalleryImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 200)
	private String caption;

	/** Detected from the file content, never taken from the client. */
	@Column(name = "content_type", nullable = false, length = 20)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private int sizeBytes;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(nullable = false, columnDefinition = "LONGBLOB")
	private byte[] data;

	/** The admin who uploaded the photo; null if that user was deleted. */
	@Column(name = "uploaded_by")
	private Long uploadedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
