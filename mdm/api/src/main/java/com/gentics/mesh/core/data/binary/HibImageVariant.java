package com.gentics.mesh.core.data.binary;

import java.io.InputStream;

import com.gentics.mesh.core.data.HibImageDataElement;
import com.gentics.mesh.core.data.node.field.HibBinaryField;
import com.gentics.mesh.core.data.storage.BinaryStorage;
import com.gentics.mesh.core.db.Supplier;
import com.gentics.mesh.core.db.Tx;
import com.gentics.mesh.core.rest.node.field.image.FocalPoint;
import com.gentics.mesh.core.result.Result;
import com.gentics.mesh.parameter.image.CropMode;
import com.gentics.mesh.parameter.image.ImageRect;
import com.gentics.mesh.parameter.image.ResizeMode;

/**
 * Domain model for binary image manipulation variant.
 * 
 * @author plyhun
 *
 */
public interface HibImageVariant extends HibImageDataElement {

	/**
	 * Get the original binary.
	 * 
	 * @return
	 */
	HibBinary getBinary(); 

	/**
	 * Get the fields, pointing to this variant.
	 * 
	 * @return
	 */
	Result<? extends HibBinaryField> findFields();

	/**
	 * Get variant width.
	 * 
	 * @return
	 */
	Integer getWidth();

	/**
	 * Get variant height.
	 * 
	 * @return
	 */
	Integer getHeight();

	/**
	 * Get focal point X coordinate.
	 * 
	 * @return
	 */
	Float getFocalPointX();

	/**
	 * Get focal point Y coordinate.
	 * 
	 * @return
	 */
	Float getFocalPointY();

	/**
	 * Get focal point zoom ratio.
	 * 
	 * @return
	 */
	Float getFocalPointZoom();

	/**
	 * Get crop rect start X coordinate.
	 * 
	 * @return
	 */
	Integer getCropStartX();

	/**
	 * Get crop rect start Y coordinate.
	 * 
	 * @return
	 */
	Integer getCropStartY();

	/**
	 * Get the variant crop mode.
	 * 
	 * @return
	 */
	CropMode getCropMode();

	/**
	 * Get the variant resize mode.
	 * 
	 * @return
	 */
	ResizeMode getResizeMode();

	/**
	 * Check if this variant is proportionally resized (i.e. comes out of "auto" mode)
	 * 
	 * @return
	 */
	boolean isAuto();

	/**
	 * Generate unique variant key.
	 *
	 * @return
	 */
	default String getKey() {
		StringBuilder builder = new StringBuilder();

		if (getCropStartX() != null) {
			builder.append("cx" + getCropStartX());
		}
		if (getCropStartY() != null) {
			builder.append("cy" + getCropStartY());
		}
		if (getCropMode() != null) {
			builder.append("crop" + getCropMode());
		}
		if (getResizeMode() != null) {
			builder.append("resize" + getResizeMode());
		}
		if (getWidth() != null) {
			builder.append("rw" + getWidth());
		}
		if (getHeight() != null) {
			builder.append("rh" + getHeight());
		}
		if (getFocalPointX() != null) {
			builder.append("fpx" + getFocalPointX().toString());
		}
		if (getFocalPointY() != null) {
			builder.append("fpy" + getFocalPointY().toString());
		}
		if (getFocalPointZoom() != null) {
			builder.append("fpz" + getFocalPointZoom());
		}
		return builder.toString();
	}

	/**
	 * Get focal point coordinates in the form of {@link FocalPoint}, or null if inapplicable.
	 * 
	 * @return
	 */
	default FocalPoint getFocalPoint() {
		Float x = getFocalPointX();
		Float y = getFocalPointY();
		if (x == null || y == null) {
			return null;
		} else {
			return new FocalPoint(x, y);
		}
	}

	/**
	 * Get crop rectangle in the form of {@link ImageRect}, or null if inapplicable.
	 * 
	 * @return
	 */
	default ImageRect getCropRect() {
		Integer cropX = getCropStartX();
		Integer cropY = getCropStartY();
		Integer cropWidth = getWidth();
		Integer cropHeight = getHeight();
		if (cropX == null || cropY == null || cropWidth == null || cropHeight == null) {
			return null;
		} else {
			return new ImageRect(cropX, cropY, cropWidth, cropHeight);
		}
	}

	/**
	 * Opens a blocking {@link InputStream} to the binary file. This should only be used for some other blocking APIs (i.e. ImageIO)
	 *
	 * @return
	 */
	default Supplier<InputStream> openBlockingStream() {
		BinaryStorage storage = Tx.get().data().binaryStorage();
		String uuid = getUuid();
		return () -> storage.openBlockingStream(uuid);
	}

	@Override
	default Integer getImageWidth() {
		return getWidth();
	}

	@Override
	default Integer getImageHeight() {
		return getHeight();
	}
}
