package team.project.TviyHub.gallery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import team.project.TviyHub.exception.BadRequestException;
import team.project.TviyHub.exception.PayloadTooLargeException;
import team.project.TviyHub.exception.ResourceNotFoundException;
import team.project.TviyHub.gallery.dto.GalleryImageResponse;
import team.project.TviyHub.user.dto.PageResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@Service
public class GalleryService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_CAPTION_LENGTH = 200;

	private final GalleryImageRepository repository;
	private final Clock clock;
	private final long maxSizeBytes;

	public GalleryService(GalleryImageRepository repository, Clock clock,
						  @Value("${app.gallery.max-size-bytes}") long maxSizeBytes) {
		this.repository = repository;
		this.clock = clock;
		this.maxSizeBytes = maxSizeBytes;
	}

	/**
	 * Saves an uploaded photo. The format is recognized by the file content, so the client-supplied
	 * name and content type are ignored.
	 */
	@Transactional
	public GalleryImageResponse upload(Long adminId, MultipartFile file, String caption) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("The file is required and must not be empty");
		}
		if (file.getSize() > maxSizeBytes) {
			throw new PayloadTooLargeException("The file must not be larger than " + (maxSizeBytes / (1024 * 1024)) + " MB");
		}
		String cleanCaption = caption == null || caption.isBlank() ? null : caption.trim();
		if (cleanCaption != null && cleanCaption.length() > MAX_CAPTION_LENGTH) {
			throw new BadRequestException("The caption must not be longer than " + MAX_CAPTION_LENGTH + " characters");
		}

		byte[] data;
		try {
			data = file.getBytes();
		} catch (IOException e) {
			throw new BadRequestException("The file could not be read");
		}
		ImageType type = ImageType.detect(data)
				.orElseThrow(() -> new BadRequestException("Only JPG, PNG and WebP images are allowed"));

		GalleryImage image = new GalleryImage();
		image.setCaption(cleanCaption);
		image.setContentType(type.contentType());
		image.setSizeBytes(data.length);
		image.setData(data);
		image.setUploadedBy(adminId);
		image.setCreatedAt(Instant.now(clock));
		GalleryImage saved = repository.saveAndFlush(image);
		return GalleryImageResponse.from(repository.findViewById(saved.getId()).orElseThrow());
	}

	/** Newest photos first; the file bytes are not loaded. */
	@Transactional(readOnly = true)
	public PageResponse<GalleryImageResponse> list(int page, int size) {
		PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		Page<GalleryImageResponse> result = repository.findAllViews(pageRequest).map(GalleryImageResponse::from);
		return PageResponse.from(result);
	}

	@Transactional(readOnly = true)
	public GalleryImage getImage(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Image " + id + " not found"));
	}

	@Transactional
	public void delete(Long id) {
		repository.delete(getImage(id));
	}
}
