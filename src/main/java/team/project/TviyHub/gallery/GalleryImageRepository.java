package team.project.TviyHub.gallery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, Long> {

	@Query(value = "select i.id as id, i.caption as caption, i.contentType as contentType, "
			+ "i.sizeBytes as sizeBytes, i.createdAt as createdAt from GalleryImage i",
			countQuery = "select count(i) from GalleryImage i")
	Page<GalleryImageView> findAllViews(Pageable pageable);

	@Query("select i.id as id, i.caption as caption, i.contentType as contentType, "
			+ "i.sizeBytes as sizeBytes, i.createdAt as createdAt from GalleryImage i where i.id = :id")
	Optional<GalleryImageView> findViewById(@Param("id") Long id);
}
