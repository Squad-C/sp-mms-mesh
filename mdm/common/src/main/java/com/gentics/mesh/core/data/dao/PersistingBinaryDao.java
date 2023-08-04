package com.gentics.mesh.core.data.dao;

import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.binary.Binaries;
import com.gentics.mesh.core.data.binary.HibBinary;
import com.gentics.mesh.core.data.binary.HibImageVariant;
import com.gentics.mesh.core.data.storage.BinaryStorage;
import com.gentics.mesh.core.db.Supplier;
import com.gentics.mesh.core.db.Transactional;
import com.gentics.mesh.core.db.Tx;
import com.gentics.mesh.core.rest.node.field.BinaryCheckStatus;
import com.gentics.mesh.core.rest.node.field.image.ImageVariantRequest;
import com.gentics.mesh.core.rest.node.field.image.ImageVariantResponse;
import com.gentics.mesh.core.rest.node.field.image.Point;
import com.gentics.mesh.core.result.Result;
import com.gentics.mesh.core.result.TraversalResult;
import com.google.common.base.Objects;

import io.reactivex.Flowable;
import io.vertx.core.buffer.Buffer;

/**
 * Persistence-aware extension to {@link BinaryDao}
 *
 * @author plyhun
 *
 */
public interface PersistingBinaryDao extends BinaryDao {

	Base64.Encoder BASE64 = Base64.getEncoder();

	/**
	 * Get a binary storage implementation.
	 *
	 * @return
	 */
	Binaries binaries();

	/**
	 * Create a database entity for image variant of the given binary.
	 * 
	 * @param binary
	 * @param inflater inflates the raw image variant
	 * @return
	 */
	HibImageVariant createPersistedVariant(HibBinary binary, ImageVariantRequest variant, Consumer<HibImageVariant> inflater);

	/**
	 * Delete the database entity of the given image variant of a given binary.
	 * 
	 * @param binary
	 * @param variant
	 */
	void deletePersistingVariant(HibBinary binary, HibImageVariant variant);

	@Override
	default Transactional<? extends HibBinary> findByHash(String hash) {
		return binaries().findByHash(hash);
	}

	@Override
	default Transactional<Stream<? extends HibBinary>> findByCheckStatus(BinaryCheckStatus checkStatus) {
		return binaries().findByCheckStatus(checkStatus);
	}

	@Override
	default Transactional<? extends HibBinary> create(String uuid, String hash, Long size, BinaryCheckStatus checkStatus) {
		return binaries().create(uuid, hash, size, checkStatus);
	}

	@Override
	default Transactional<Stream<HibBinary>> findAll() {
		return binaries().findAll();
	}

	@Override
	default Supplier<InputStream> openBlockingStream(HibBinary binary) {
		return binary.openBlockingStream();
	}

	@Override
	default Flowable<Buffer> getStream(HibBinary binary) {
		BinaryStorage storage = Tx.get().data().binaryStorage();
		return storage.read(binary.getUuid());
	}

	@Override
	default String getBase64ContentSync(HibBinary binary) {
		Buffer buffer = Tx.get().data().binaryStorage().readAllSync(binary.getUuid());
		return BASE64.encodeToString(buffer.getBytes());
	}

	@SuppressWarnings("unchecked")
	@Override
	default Result<? extends HibImageVariant> createVariants(HibBinary binary, Collection<ImageVariantRequest> requests, InternalActionContext ac, boolean deleteOther) {
		if (!isImage(binary)) {
			// TODO own error
			throw error(BAD_REQUEST, "image_error_reading_failed");
		}
		Result<? extends HibImageVariant> oldVariants = getVariants(binary, ac);
		Map<ImageVariantRequest, Optional<HibImageVariant>> requestExistence = requests.stream()
				.map(request -> Pair.of(request, oldVariants.stream().filter(variant -> doesVariantMatchRequest(variant, request)).map(HibImageVariant.class::cast).findAny()))
				.collect(Collectors.toMap(Pair::getKey, Pair::getValue, (a,b) -> a));

		List<HibImageVariant> newVariants = requestExistence.entrySet().stream()
				.map(pair -> pair.getValue().orElseGet(() -> createVariant(binary, pair.getKey(), ac, false)))
				//.map(newVariant -> transformToRestSync(newVariant, ac, level))
				.collect(Collectors.toList());
		
		if (deleteOther) {
			List<ImageVariantRequest> toDelete = ((List<HibImageVariant>) ListUtils.subtract(oldVariants.list(), newVariants)).stream().map(deletable -> transformToRestSync(deletable, ac, 0).toRequest()).collect(Collectors.toList());
			return deleteVariants(binary, toDelete, ac, false);
		} else {
			return new TraversalResult<>(ListUtils.sum(newVariants, oldVariants.list()));
		}
	}

	@Override
	default Result<? extends HibImageVariant> deleteVariants(HibBinary binary, Collection<ImageVariantRequest> requests, InternalActionContext ac, boolean deleteOther) {
		if (!isImage(binary)) {
			// TODO own error
			throw error(BAD_REQUEST, "image_error_reading_failed");
		}
		if (deleteOther) {
			Result<? extends HibImageVariant> oldVariants = getVariants(binary, ac);
			List<ImageVariantRequest> finalRequests = new ArrayList<>(requests);
			requests = oldVariants.stream()
					.filter(oldVariant -> finalRequests.stream().anyMatch(request -> doesVariantMatchRequest(oldVariant, request)))
					.map(oldVariant -> transformToRestSync(oldVariant, ac, 0).toRequest())
					.collect(Collectors.toList());
		}
		requests.stream().forEach(request -> deleteVariant(binary, request, ac, false));
		return getVariants(binary, ac);
	}

	@Override
	default ImageVariantResponse transformToRestSync(HibImageVariant element, InternalActionContext ac, int level,	String... languageTags) {
		ImageVariantResponse response = new ImageVariantResponse()
			.setWidth(element.getWidth())
			.setHeight(element.getHeight())
			.setAuto(element.isAuto())
			.setCropMode(element.getCropMode())
			.setFocalPoint(element.getFocalPoint())
			.setFocalZoom(element.getFocalPointZoom())
			.setOrigin(false)
			.setRect(element.getCropRect())
			.setResizeMode(element.getResizeMode());

		if (level > 0) {
			response.setFileSize(element.getSize());
		}
		return response;
	}

	@Override
	default HibImageVariant createVariant(HibBinary binary, ImageVariantRequest variant, InternalActionContext ac, boolean throwOnExisting) {
		return createPersistedVariant(binary, variant, entity -> {
			if (variant.getRect() != null) {
				entity.setCropStartX(variant.getRect().getStartX());
				entity.setCropStartY(variant.getRect().getStartY());
				entity.setWidth(variant.getRect().getWidth());
				entity.setHeight(variant.getRect().getHeight());
			} else {
				entity.setCropStartX(null);
				entity.setCropStartY(null);			
				if (variant.getWidth() != null) {
					if ("auto".equals(variant.getWidth())) {
						entity.setAuto(true);
						entity.setHeight(Integer.parseInt(variant.getHeight()));
						Point originalSize = binary.getImageSize();
						float ratio = ((float) originalSize.getX()) / ((float) originalSize.getY());
						entity.setWidth((int) ((float) entity.getHeight() * ratio));
					} else {
						entity.setWidth(Integer.parseInt(variant.getWidth()));
					}
				} else {
					entity.setWidth(null);
				}
				if (variant.getHeight() != null) {
					if ("auto".equals(variant.getHeight())) {
						entity.setAuto(true);
						entity.setWidth(Integer.parseInt(variant.getWidth()));
						Point originalSize = binary.getImageSize();
						float ratio = ((float) originalSize.getX()) / ((float) originalSize.getY());
						entity.setHeight((int) ((float) entity.getWidth() * ratio));
					} else {
						entity.setHeight(Integer.parseInt(variant.getHeight()));
					}
				} else {
					entity.setHeight(null);
				}
			}
			if (variant.getFocalPoint() != null) {
				entity.setFocalPointX(variant.getFocalPoint().getX());
				entity.setFocalPointY(variant.getFocalPoint().getY());
			} else {
				entity.setFocalPointX(null);
				entity.setFocalPointY(null);
			}
			entity.setCropMode(variant.getCropMode());
			entity.setFocalPointZoom(variant.getFocalPointZoom());
			entity.setResizeMode(variant.getResizeMode());
		});
	}

	@Override
	default void deleteVariant(HibBinary binary, ImageVariantRequest variant, InternalActionContext ac, boolean throwOnAbsent) {
		
	}

	/**
	 * Check if an existing image variant matches the variant creation request.
	 * 
	 * @param variant
	 * @param request
	 * @return
	 */
	default boolean doesVariantMatchRequest(HibImageVariant variant, ImageVariantRequest request) {
		if (!Objects.equal(variant.getFocalPointZoom(), request.getFocalPointZoom())) {
			return false;
		}
		if (!Objects.equal(variant.getCropMode(), request.getCropMode())) {
			return false;
		}
		if (!Objects.equal(variant.getResizeMode(), request.getResizeMode())) {
			return false;
		}
		if (!Objects.equal(variant.getFocalPoint(), request.getFocalPoint())) {
			return false;
		}
		if (request.getRect() != null && !Objects.equal(variant.getCropRect(), request.getRect())) {
			return false;
		}
		if (request.getWidth() != null && variant.getWidth() != null) {
			if (!"auto".equals(request.getWidth())) {
				if (!request.getWidth().equals(String.valueOf(variant.getWidth()))) {
					return false;
				}
			} else if (!variant.isAuto()) {
				return false;
			}
		}
		if (request.getHeight() != null && variant.getHeight() != null) {
			if (!"auto".equals(request.getHeight())) {
				if (!request.getHeight().equals(String.valueOf(variant.getHeight()))) {
					return false;
				}
			} else if (!variant.isAuto()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Transform an original binary into its variant REST model
	 * 
	 * @param binary
	 * @param ac
	 * @param fillFilesize
	 * @return
	 */
	default ImageVariantResponse transformBinaryToRestVariantSync(HibBinary binary, InternalActionContext ac, boolean fillFilesize) {
		ImageVariantResponse response = new ImageVariantResponse()
				.setWidth(binary.getImageWidth())
				.setHeight(binary.getImageHeight())
				.setAuto(false)
				.setCropMode(null)
				.setFocalPoint(null)
				.setFocalZoom(null)
				.setOrigin(true)
				.setRect(null)
				.setResizeMode(null);

			if (fillFilesize) {
				response.setFileSize(binary.getSize());
			}
			return response;
	}

	/**
	 * Check if binary is a graphic image.
	 * 
	 * @param binary
	 * @return
	 */
	static boolean isImage(HibBinary binary) {
		return binary.getImageSize() != null;
	}
}
