package com.gentics.mesh.parameter;

import com.gentics.mesh.core.rest.SortOrder;
import com.gentics.mesh.util.NumberUtils;

/**
 * Interface for paging query parameters.
 */
public interface PagingParameters extends ParameterProvider {

	public static final String PAGE_PARAMETER_KEY = "page";
	public static final String PER_PAGE_PARAMETER_KEY = "perPage";
	public static final String SORT_BY_PARAMETER_KEY = "sortBy";
	public static final String SORT_ORDER_PARAMETER_KEY = "order";

	public static final int DEFAULT_PAGE = 1;
	public static final SortOrder DEFAULT_SORT_ORDER = SortOrder.UNSORTED;

	/**
	 * Return the current page.
	 * 
	 * @return Current page number
	 */
	default int getPage() {
		return NumberUtils.toInt(getParameter(PAGE_PARAMETER_KEY), DEFAULT_PAGE);
	}

	/**
	 * Return the per page count.
	 * 
	 * @return Per page count
	 */
	default Long getPerPage() {
		return NumberUtils.toLong(getParameter(PER_PAGE_PARAMETER_KEY), null);
	}

	/**
	 * Set the current page.
	 * 
	 * @param page
	 *            Current page number
	 * @return Fluent API
	 */
	default PagingParameters setPage(long page) {
		setParameter(PAGE_PARAMETER_KEY, String.valueOf(page));
		return this;
	}

	/**
	 * Set the per page count.
	 * 
	 * @param perPage
	 *            Per page count
	 * @return Fluent API
	 */
	default PagingParameters setPerPage(Long perPage) {
		if (perPage != null) {
			setParameter(PER_PAGE_PARAMETER_KEY, String.valueOf(perPage));
		}
		return this;
	}

	/**
	 * Return the sort by parameter value.
	 * 
	 * @return Field to be sorted by
	 */
	default String getSortBy() {
		return getParameter(SORT_BY_PARAMETER_KEY);
	}

	/**
	 * Return the sortorder.
	 * 
	 * @return
	 */
	default SortOrder getOrder() {
		return SortOrder.valueOfName(getParameter(SORT_ORDER_PARAMETER_KEY));

	}

	/**
	 * Set the sort by parameter.
	 * 
	 * @param sortBy
	 * @return
	 */
	default PagingParameters setSortBy(String sortBy) {
		setParameter(SORT_BY_PARAMETER_KEY, sortBy);
		return this;
	}

	/**
	 * Set the used sort order.
	 * 
	 * @param sortBy
	 *            Sort order
	 * @return Fluent API
	 * 
	 */
	default PagingParameters setSortOrder(String sortBy) {
		setParameter(SORT_BY_PARAMETER_KEY, sortBy);
		return this;
	}

}
