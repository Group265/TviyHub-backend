package team.project.TviyHub.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "One page of a list")
public record PageResponse<T>(
		@Schema(description = "Items of this page") List<T> content,
		@Schema(description = "Page number, starting from 0", example = "0") int page,
		@Schema(description = "Requested page size (at most 100)", example = "20") int size,
		@Schema(description = "Number of items in all pages", example = "57") long totalElements,
		@Schema(description = "Number of pages", example = "3") int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}
}
