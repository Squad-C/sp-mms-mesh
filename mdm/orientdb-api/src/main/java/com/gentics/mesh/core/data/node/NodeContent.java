package com.gentics.mesh.core.data.node;

import java.util.List;

import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.dao.ContentDaoWrapper;
import com.gentics.mesh.core.db.Tx;
import com.gentics.mesh.core.rest.common.ContainerType;

/**
 * Container object for handling nodes in combination with a specific known container.
 */
public class NodeContent {

	private Node node;
	private NodeGraphFieldContainer container;
	private List<String> languageFallback;
	private ContainerType type;

	/**
	 * Create a new node content.
	 * 
	 * @param node
	 * @param container
	 * @param languageFallback
	 *            Language fallback list which was used to load the content
	 */
	public NodeContent(Node node, NodeGraphFieldContainer container, List<String> languageFallback, ContainerType type) {
		this.node = node;
		this.container = container;
		this.languageFallback = languageFallback;
		this.type = type;
	}

	public Node getNode() {
		ContentDaoWrapper contentDao = Tx.get().data().contentDao();
		if (node == null && container != null) {
			node = contentDao.getNode(container);
		}
		return node;
	}

	public NodeGraphFieldContainer getContainer() {
		return container;
	}

	public List<String> getLanguageFallback() {
		return languageFallback;
	}

	public ContainerType getType() {
		return type;
	}

}
