/**
 * Copyright (C) 2014 OpenTravel Alliance (info@opentravel.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opentravel.schemas.node;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.opentravel.schemacompiler.loader.LibraryLoaderException;
import org.opentravel.schemacompiler.model.AbstractLibrary;
import org.opentravel.schemacompiler.model.BuiltInLibrary;
import org.opentravel.schemacompiler.model.LibraryElement;
import org.opentravel.schemacompiler.model.LibraryMember;
import org.opentravel.schemacompiler.model.TLContext;
import org.opentravel.schemacompiler.model.TLLibrary;
import org.opentravel.schemacompiler.model.TLLibraryStatus;
import org.opentravel.schemacompiler.model.TLResource;
import org.opentravel.schemacompiler.model.TLService;
import org.opentravel.schemacompiler.model.XSDComplexType;
import org.opentravel.schemacompiler.model.XSDElement;
import org.opentravel.schemacompiler.model.XSDLibrary;
import org.opentravel.schemacompiler.model.XSDSimpleType;
import org.opentravel.schemacompiler.repository.Project;
import org.opentravel.schemacompiler.repository.ProjectItem;
import org.opentravel.schemacompiler.repository.RemoteRepository;
import org.opentravel.schemacompiler.repository.RepositoryException;
import org.opentravel.schemacompiler.repository.RepositoryItemState;
import org.opentravel.schemacompiler.util.ContextUtils;
import org.opentravel.schemacompiler.util.URLUtils;
import org.opentravel.schemas.controllers.ContextController;
import org.opentravel.schemas.controllers.ProjectController;
import org.opentravel.schemas.modelObject.ModelObjectFactory;
import org.opentravel.schemas.node.interfaces.ComplexComponentInterface;
import org.opentravel.schemas.node.interfaces.INode;
import org.opentravel.schemas.node.interfaces.ResourceMemberInterface;
import org.opentravel.schemas.node.interfaces.SimpleComponentInterface;
import org.opentravel.schemas.node.listeners.ListenerFactory;
import org.opentravel.schemas.node.resources.ResourceNode;
import org.opentravel.schemas.preferences.GeneralPreferencePage;
import org.opentravel.schemas.properties.Images;
import org.opentravel.schemas.stl2developer.OtmRegistry;
import org.opentravel.schemas.types.TypeProvider;
import org.opentravel.schemas.types.TypeResolver;
import org.opentravel.schemas.types.TypeUser;
import org.opentravel.schemas.types.WhereUsedLibraryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LibraryNode class manages an internal navigation oriented node a library model class. Libraries are model classes
 * that contain named members representing global types and elements from either schemas (XSD), built-in-types or OTA2
 * model components.
 * 
 * QUESTION - why host the TL abstract library directly instead of through MO? MO are supposed to be a isolation layer
 * (shearing layer) between the schema driven TLModel and the GUI driven node model.
 */

public class LibraryNode extends Node {
	private static final Logger LOGGER = LoggerFactory.getLogger(LibraryNode.class);

	protected static final String DEFAULT_LIBRARY_TYPE = "Library";
	protected static final String XSD_LIBRARY_TYPE = "XSD Library";
	protected static final String TempLib = "TemporaryLibrary";

	public static final String COMPLEX_OBJECTS = "Complex Objects";
	public static final String SIMPLE_OBJECTS = "Simple Objects";
	public static final String RESOURCES = "Resources";
	public static final String ELEMENTS = "Elements";

	protected NavNode complexRoot;
	protected NavNode simpleRoot;
	protected NavNode elementRoot;
	protected ServiceNode serviceRoot;
	protected NavNode resourceRoot;

	protected WhereUsedLibraryHandler whereUsedHandler = null;

	protected AbstractLibrary absTLLibrary; // Underlying TL library model object
	protected TLLibrary genTLLib = null; // TL library for generated components.
	protected ProjectItem projectItem; // The TL Project Item wrapped around this library

	protected NamespaceHandler nsHandler;

	protected String curContext; // The current default context id to use for this library.
	private boolean editable = false;

	/**
	 * Default constructor. Used for user-directed library creation. Names type node "Library" and does <i>not</i>
	 * create navNodes.
	 */
	public LibraryNode(Node parent) {
		super();
		setLibrary(this);
		absTLLibrary = new TLLibrary();
		setParent(parent);
		getParent().linkLibrary(this);
		this.setName("");
		nsHandler = NamespaceHandler.getNamespaceHandler((ProjectNode) parent);
		this.setNamespace(parent.getNamespace());
		// LOGGER.debug("Created empty library without underlying model");

	}

	public WhereUsedLibraryHandler getWhereUsedHandler() {
		if (whereUsedHandler == null)
			whereUsedHandler = new WhereUsedLibraryHandler(this);
		return whereUsedHandler;
	}

	/**
	 * Create a library node and all of its children based on the TL model library. Link to the passed parent node. If
	 * the parent is not a project or Nav node, it will be set to the default project. May be invoked with an new, empty
	 * library.
	 * 
	 * Note - invoker must set namespace and prefix.
	 * 
	 * @param alib
	 * @param parent
	 *            - if null, library is added to default project
	 */
	public LibraryNode(final AbstractLibrary alib, final Node parent) {
		super(alib.getName());
		if (parent instanceof VersionAggregateNode)
			LOGGER.debug("Begin creating new library: " + alib.getPrefix() + ":" + alib.getName() + " in aggregate "
					+ parent.getParent());
		else
			LOGGER.debug("Begin creating new library: " + alib.getName() + " in " + parent);

		setLibrary(this);
		absTLLibrary = alib;
		setParent(parent);
		ProjectController pc = OtmRegistry.getMainController().getProjectController();
		if (parent == null)
			setParent(pc.getDefaultProject());
		else if ((!(parent instanceof ProjectNode) && !(parent instanceof NavNode)))
			setParent(pc.getDefaultProject());
		getParent().linkLibrary(this);

		ProjectItem pi = null;
		for (ProjectItem item : getProject().getProject().getProjectItems()) {
			if (item.getContent() == alib) {
				pi = item;
				break; // todo - delegate to project.get(alib)
			}
		}
		if (pi == null) {
			try {
				pi = getProject().getProject().getProjectManager()
						.addUnmanagedProjectItem(alib, getProject().getProject());
			} catch (RepositoryException e1) {
				LOGGER.error("Error adding " + alib.getName() + " to project.");
			}
		}
		setProjectItem(pi);

		nsHandler = NamespaceHandler.getNamespaceHandler(this);
		nsHandler.registerLibrary(this);

		modelObject = ModelObjectFactory.newModelObject(alib, this);

		complexRoot = new NavNode(COMPLEX_OBJECTS, this);
		simpleRoot = new NavNode(SIMPLE_OBJECTS, this);
		resourceRoot = new NavNode(RESOURCES, this);

		// Let the tools edit the library during construction.
		setEditable(true);

		// Process all the children
		generateModel(alib);

		// Save edit state: Test to see if this is an editable library.
		updateLibraryStatus();
		setIdentity(getLabel());

		// Set up the contexts
		addContexts();
		LOGGER.debug("Library created: " + this.getName());
	}

	private void addListeners() {
		ListenerFactory.setListner(this);
	}

	private void removeListeners() {
		ListenerFactory.clearListners(this);
	}

	public LibraryNode(ProjectItem pi, ProjectNode projectNode) {
		this(pi.getContent(), projectNode);
		projectItem = pi;

		// Save edit state: may be different with Project Item.
		// Make sure library is in the managed namespace of the project.
		updateLibraryStatus();
	}

	public LibraryNode(ProjectItem pi, LibraryChainNode chain) {
		this(pi.getContent(), chain.getVersions());
		for (Node members : getDescendentsNamedTypes()) {
			if (members instanceof ComponentNode) {
				chain.add((ComponentNode) members);
			}
		}
		ServiceNode serviceNode = getService();
		if (serviceNode != null) {
			chain.add(serviceNode);
		}
		// Do NOT add resource here. It is done in addMember().
		// TODO - fix service to match resource (or vice versa)
		// for (ResourceNode r : getResources())
		// chain.add(r);

		projectItem = pi;

		// Save edit state: may be different with Project Item.
		// Make sure library is in the managed namespace of the project.
		updateLibraryStatus();
	}

	public NamespaceHandler getNsHandler() {
		return nsHandler;
	}

	/**
	 * Using the library status and namespace policies set the library editable state. Use when changing the library's
	 * owner or namespace.
	 */
	public void updateLibraryStatus() {
		editable = isAbsLibEditable();
		// Override for managed namespaces
		if (GeneralPreferencePage.areNamespacesManaged()) {
			if (isInChain()) {
				if (getEditStatus().equals(NodeEditStatus.MANAGED_READONLY))
					this.editable = false;
				else if (getEditStatus().equals(NodeEditStatus.NOT_EDITABLE))
					this.editable = false;
			} else if (!isInProjectNS())
				this.editable = false;
		}
		getEditableState(); // dead and duplicate code?
	}

	@Override
	public boolean isEditable() {
		return this.editable;
	}

	private boolean getEditableState() {
		if (isDeleted()) {
			return false;
		}
		boolean editable = isAbsLibEditable();
		if (GeneralPreferencePage.areNamespacesManaged()) {
			if (isInChain()) {
				if (getEditStatus().equals(NodeEditStatus.MANAGED_READONLY))
					editable = false;
				else if (getEditStatus().equals(NodeEditStatus.NOT_EDITABLE))
					editable = false;
			} else if (!isInProjectNS())
				editable = false;
		}
		return editable;
	}

	/**
	 * Override the namespace policy and TL Library status and set to editable. <b>Caution</b> can cause the GUI and TL
	 * Model to get out of sync.
	 * 
	 * @param true to enable edits on this library
	 */
	public void setEditable(boolean editable) {
		this.editable = editable;
	}

	/**
	 * @return true if namespaces are unmanaged OR namespaces are managed and this library is in its project namespace.
	 */
	public boolean isEditableByNamespacePolicy() {
		if (!GeneralPreferencePage.areNamespacesManaged())
			return true;
		return getNamespace().contains(getParent().getNamespace());
	}

	public boolean isEmpty() {
		if (complexRoot.isEmpty())
			if (simpleRoot.isEmpty())
				if (resourceRoot.isEmpty())
					if (serviceRoot == null || serviceRoot.isEmpty())
						return true;
		return false;
	}

	/**
	 * Import a list of nodes to this library. Imports nodes then replaces type assignments.
	 * 
	 * @param global
	 *            if true all nodes that use the imported node as a type will be changed to use the imported node. If
	 *            false, only those in the current library will be changed.
	 * @return list of imported nodes without those not imported.
	 */
	// TODO - there is something wrong with the changing of type use for read-only libraries.
	public List<Node> importNodes(List<Node> sourceList, boolean global) {
		ArrayList<Node> imported = new ArrayList<Node>();
		final Map<Node, Node> sourceToNewMap;

		// Do the import. Nodes are typed, but not used.
		sourceToNewMap = importNodes(sourceList);
		// LOGGER.debug("Imported " + sourceToNewMap.size() + " nodes. Ready to fix type assignments.");

		// Change type users to use the imported nodes.
		LibraryNode scopeLib = this;
		if (global)
			scopeLib = null;
		for (final Entry<Node, Node> entry : sourceToNewMap.entrySet()) {
			final Node sourceNode = entry.getKey();
			imported.add(entry.getValue());
			// if (global)
			// sourceNode.replaceTypesWith(entry.getValue());
			// else
			sourceNode.replaceTypesWith(entry.getValue(), scopeLib);
		}

		return imported;
	}

	/**
	 * Import a list of nodes to this library. Fixes the names of the imported components.
	 * 
	 * @return list of imported nodes excluding those not imported.
	 */
	public Map<Node, Node> importNodes(List<Node> sourceList) {
		// insertion ordered
		final Map<Node, Node> sourceToNewMap = new LinkedHashMap<Node, Node>(sourceList.size());

		// Create aliases if the source has multiple properties which use the same complex type.
		// Must be done before imports to assure all referenced types have the aliases added.
		for (final Node source : sourceList) {
			if (source instanceof CoreObjectNode || source instanceof BusinessObjectNode)
				((ComponentNode) source).createAliasesForProperties();
		}

		NodeVisitor nameFixer = new NodeVisitors().new FixNames();
		for (final Node source : sourceList) {
			final Node newNode = importNode(source);
			if (newNode != null && newNode != source) {
				nameFixer.visit(newNode);
				sourceToNewMap.put(source, newNode);
			} else
				LOGGER.warn("Import duplicate excluded from map: " + newNode);
		}

		collapseContexts();

		new TypeResolver().resolveTypes(this);

		// LOGGER.info("ImportNodes() imported " + sourceToNewMap.size() + " nodes. ");
		return sourceToNewMap;
	}

	/**
	 * Import a node to this library. Clone the node and underlying models and add it to the library.
	 * 
	 * NOTE: newly imported node is UN-TYPED.
	 * 
	 * @param source
	 * @return - new node created, source if it already was in library, or null on error.
	 */
	public Node importNode(final Node source) {
		// LOGGER.debug("Importing source node: " + source.getName());
		if (source.getLibrary() == this) {
			LOGGER.error("Tried to import to same library: " + this.getName());
			return source; // nothing to do.
		}
		if (!importNodeCheck(source))
			return null;

		ContextUtils.resolveApplicationContexts((LibraryMember) source.getModelObject().getTLModelObj());
		Node newNode = NodeFactory.newComponent_UnTyped((LibraryMember) source.cloneTLObj());

		// Node newNode = source.clone(this, null);
		if (newNode == null) {
			LOGGER.warn("Could not clone " + source + " a " + source.getClass().getSimpleName());
			return null;
		}

		// FIXME - rip out all this context handling and just use this library's context
		//
		// Re-map context ID's in the cloned object and copy over any contexts for the target
		// library that
		// do not already exist
		Object sourceTLObj = source.getModelObject().getTLModelObj();
		Object newTLObj = newNode.getModelObject().getTLModelObj();
		AbstractLibrary targetLib = getTLaLib();

		if ((newTLObj instanceof LibraryElement) && (targetLib instanceof TLLibrary)) {
			LibraryElement sourceLibElement = (LibraryElement) sourceTLObj;
			TLLibrary targetLibrary = (TLLibrary) getTLaLib();

			if (sourceLibElement.getOwningLibrary() instanceof TLLibrary) {
				ContextUtils.translateContextIdReferences((LibraryElement) newTLObj,
						(TLLibrary) sourceLibElement.getOwningLibrary(), targetLibrary);
				// Patch - translate can create context with empty ID. this patch is a work around.
				// TODO - still need to prevent it.
				for (TLContext ctx : targetLibrary.getContexts()) {
					if (ctx.getContextId().isEmpty())
						ctx.setContextId("Imported");
					if (ctx.getContextId().equals(ctx.getApplicationContext())) {
						LOGGER.error("Context id is equal to application context" + ctx.getContextId());
						// TODO - this only creates duplicate contexts with multiple id values which are later
						// automatically appended with a number to be unique
						ctx.setContextId("Imported");
					}
				}
			}
		}
		addMember(newNode);

		if (!(newNode instanceof EnumerationClosedNode)) {
			newNode.setExtensible(true);
		}
		return (newNode);
	}

	/**
	 * @return false if not a TL library, source is not a library member or library is not editable
	 */
	private boolean importNodeCheck(Node source) {
		if (this.getTLLibrary() == null) {
			LOGGER.error("Tried to import source node to non-TL library: " + this.getName());
			return false;
		}
		if ((source.getTLModelObject() == null) || !(source.getTLModelObject() instanceof LibraryMember)) {
			LOGGER.error("Exit - not a LibraryMember: " + source.getName());
			return false;
		}
		if (!this.isEditable()) {
			LOGGER.error("Tried to import to a read-only library: " + this.getName());
			return false;
		}
		return true;
	}

	/**
	 * True if the compiler reports the library is editable. Also checks the file system to see if it can write to the
	 * file.
	 * 
	 */
	public boolean isAbsLibEditable() {
		boolean isEditable = (absTLLibrary != null) && !absTLLibrary.isReadOnly();
		// override with false depending on repository state.
		if (getProjectItem() != null) {
			if (getProjectItem().getState().equals(RepositoryItemState.MANAGED_UNLOCKED))
				isEditable = false;
			if (getProjectItem().getState().equals(RepositoryItemState.MANAGED_LOCKED))
				isEditable = false;
		}
		return isEditable;
	}

	public TLLibraryStatus getStatus() {
		TLLibraryStatus status = TLLibraryStatus.DRAFT;
		return getProjectItem() == null ? status : getProjectItem().getStatus();
	}

	/**
	 * Add a tlContext to the library if none exists. Then, if this is a TL Library add the context to the context
	 * controller. If the TLLibrary associated with this library does not have a context, make one.
	 */
	protected void addContexts() {
		ContextController cc = OtmRegistry.getMainController().getContextController();
		if (cc == null)
			throw new IllegalStateException("Context Controller not registered before use.");

		TLLibrary lib = getTLLibrary();
		if (isEditable() && lib.getContexts().size() > 1)
			collapseContexts();

		if (lib.getContexts().isEmpty()) {
			TLContext tlc = new TLContext();
			lib.addContext(tlc);

			tlc.setContextId(defautIfNullOrEmpty(lib.getPrefix(), "default"));
			tlc.setApplicationContext(defautIfNullOrEmpty(lib.getNamespace(), "Default"));
		}

		if (isTLLibrary())
			cc.addContexts(this);
	}

	/**
	 * Collapse all contexts in this library (context model and tlLibrary) down to the default context. All contents of
	 * the library may be changed to merge contexts to the default.
	 */
	// Collapse all contexts down to one. Temporary fix that may be in place for years.
	protected void collapseContexts() {
		// LOGGER.debug("Ready to merge contexts for library: " + this);
		if (!(getTLaLib() instanceof TLLibrary)) {
			LOGGER.error("Error. Not a valid library for collapseContexts.");
			return;
		}

		// If there are no context then we are done.
		if (getTLLibrary().getContexts().isEmpty())
			return;

		// tlc is the context to keep.
		TLContext tlc = getTLLibrary().getContext(getDefaultContextId());
		if (tlc == null)
			tlc = getTLLibrary().getContexts().get(0); // there must be at least one

		// If there is only one TLContext then make sure context manager matches.
		if (getTLLibrary().getContexts().size() == 1) {
			// assert (cc.getAvailableContextIds(this).size() == 1);
			// assert (cc.getAvailableContextIds(this).size() == getTLLibrary().getContexts().size());
			// assert (cc.getAvailableContextIds(this).get(0).equals(tlc.getContextId()));
			return; // all done. if any child used a different context the TLLibrary would have more than 1.
		}

		// More than one context is being used. Merge context of children the collapse down the unused contexts.
		//
		// Merge contexts in all children of this library.
		for (Node n : getDescendants_NamedTypes()) {
			n.mergeContext(tlc.getContextId());
		}

		// Now remove the unused contexts
		List<TLContext> contexts = new ArrayList<TLContext>(getTLLibrary().getContexts());
		for (TLContext tc : contexts) {
			if (tc != tlc) {
				getTLLibrary().removeContext(getTLLibrary().getContext(tc.getContextId()));
				// LOGGER.debug("removed " + tc.getContextId() + " from tlLibrary " + this);
			}
		}
		// TODO - Make the context manager agree with the tllibrary
		// cc.clearContexts(this);
		// cc.newContext(this, tlc.getContextId(), tlc.getApplicationContext());
		// FAILS - cm.addNode(this, tlc);
		// assert (cc.getAvailableContextIds(this).size() == 1);
		// assert (cc.getAvailableContextIds(this).size() == getTLLibrary().getContexts().size());
		// assert (cc.getAvailableContextIds(this).get(0).equals(tlc.getContextId()));

		// LOGGER.debug("merged contexts into context " + tlc.getContextId());
	}

	/**
	 * Create the library for the generated components.
	 * 
	 * @param xLib
	 */
	protected void makeGeneratedComponentLibrary(AbstractLibrary xLib) {
		genTLLib = new TLLibrary();
		if (xLib == null) {
			genTLLib.setNamespace("uri:namespaces:temporaryNS");
			genTLLib.setPrefix("tmp");
			genTLLib.setName(TempLib);
			try {
				genTLLib.setLibraryUrl(new URL("temp"));
			} catch (MalformedURLException e) {
				LOGGER.error("Invalid URL exception");
			}
		} else {
			genTLLib.setNamespace(xLib.getNamespace());
			genTLLib.setPrefix(xLib.getPrefix());
			genTLLib.setName(xLib.getName());
			genTLLib.setLibraryUrl(xLib.getLibraryUrl());
			// genTLLib.setOwningModel(getTLModel()); // TEST - TEST - TEST
		}
		setEditable(false);
	}

	/**
	 * @return - the LTLibrary created to hold generated otm model elements
	 */
	public TLLibrary getGeneratedLibrary() {
		return genTLLib;
	}

	@Override
	public Image getImage() {
		final ImageRegistry imageRegistry = Images.getImageRegistry();
		if (getTLaLib() instanceof XSDLibrary) {
			return imageRegistry.get(Images.builtInLib);
		}
		if (getTLaLib() instanceof BuiltInLibrary) {
			return imageRegistry.get(Images.builtInLib);
		}
		return imageRegistry.get(Images.library);
	}

	/**
	 * Uses linkChild() to link Node to the appropriate navigation node in this library. Does family processing. Does
	 * set library and contexts. Does <b>not</b> impact the TL model. Does <b>not</b> do aggregate processing.
	 * 
	 * @return
	 */
	public boolean linkMember(Node n) {
		if (n == null)
			throw new IllegalArgumentException("Null parameter.");
		if (n.getName().isEmpty())
			throw new IllegalArgumentException("Node must have a name.");
		boolean linkOK = true;

		// LOGGER.debug("Linking node: "+n.getName());
		if (n instanceof ComplexComponentInterface)
			linkOK = complexRoot.linkChild(n);
		else if (n instanceof SimpleComponentInterface)
			linkOK = simpleRoot.linkChild(n);
		else if (n instanceof ResourceNode) {
			linkOK = getResourceRoot().getChildren().add(n);
			n.setParent(getResourceRoot());
		} else if (n instanceof ServiceNode)
			; // Nothing to do because services are already linked to library.
		else if (n.isXsdElementAssignable())
			// TODO - i don't think this is ever reached. ElementRoot is never accessed.
			linkOK = elementRoot.linkChild(n.getXsdNode());
		else
			LOGGER.error("linkMember is trying to add unknown object type: " + n + ":" + n.getClass().getSimpleName());
		// I don't know why but only service node creates stack overflow.
		// Services can't be moved, so they will never have to change their lib.
		if (linkOK) {
			if (!(n instanceof ServiceNode)) {
				n.setLibrary(this);
				n.setKidsLibrary();
			}
			addContext(n);
		}
		return linkOK;
	}

	/**
	 * Lock this library in its repository. User repository controller to handle exceptions with user dialogs.
	 * 
	 * @throws RepositoryException
	 * @throws LibraryLoaderException
	 */
	// TODO - why is lock managed here while unlock is managed in project manager?
	// only called by run in repo controller and by tests in repository controller test
	public void lock() throws RepositoryException, LibraryLoaderException {
		ProjectNode pn = getProject();
		if (pn == null)
			throw new RepositoryException("Library was not part of a project");

		// String path = pn.getProject().getProjectFile().getAbsolutePath();
		File dir = pn.getProject().getProjectFile().getParentFile();

		// FIXME - don't get the list.
		// try to lock - if throws exception then refresh and try again.

		// 5/2016 - dmh - refresh the project to assure the most current version is being locked.
		List<ProjectItem> updated = pn.getProject().getProjectManager().refreshManagedProjectItems();
		// pn.getProject().getProjectManager().refreshManagedProjectItems();

		pn.getProject().getProjectManager().lock(getProjectItem(), dir);
		// LOGGER.debug("Locked library which created local file: " + path);
		setEditable(isAbsLibEditable());
	}

	/**
	 * Unlock this library in its repository.
	 * 
	 * @return empty string if successful or an error message from repository.
	 */
	// Not Used
	public void unlock() throws RepositoryException {
		ProjectNode pn = getProject();
		if (pn == null)
			throw new RepositoryException("Library was not part of a project");

		OtmRegistry.getMainController().getRepositoryController().unlockAndRevert(this);
		// pn.getProject().getProjectManager().unlock(getProjectItem(), true);
		// LOGGER.debug("UnLocked library " + this);
		setEditable(isAbsLibEditable());
	}

	/**
	 * Use the generator appropriate to the library type. Gets each member of the abstract library and creates the
	 * appropriate nodes and links them to the library.
	 * 
	 * NOTE - resolveTypes must be used to set type nodes and owners.
	 * 
	 * @param alib
	 *            - abstract library from TL model
	 */
	protected void generateModel(final AbstractLibrary alib) {
		// LOGGER.debug("Generating library "+alib.getName()+".");
		if (alib instanceof XSDLibrary)
			generateLibrary((XSDLibrary) alib);
		else if (alib instanceof BuiltInLibrary)
			generateLibrary((BuiltInLibrary) alib);
		else if (alib instanceof TLLibrary)
			generateLibrary((TLLibrary) alib);
	}

	private void generateLibrary(final BuiltInLibrary biLib) {
		Node n;
		XsdNode xn;
		boolean hasXsd = false;
		elementRoot = new NavNode(ELEMENTS, this);
		if (genTLLib == null)
			makeGeneratedComponentLibrary(biLib);
		for (final LibraryMember mbr : biLib.getNamedMembers()) {
			n = GetNode(mbr);
			if ((mbr instanceof XSDSimpleType) || (mbr instanceof XSDComplexType) || (mbr instanceof XSDElement)) {
				hasXsd = true;
				if (n == null) {
					xn = new XsdNode(mbr, this);
					n = xn.getOtmModel();
					xn.setXsdType(true);
				}
				if (n == null)
					continue;
				n.setXsdType(true); // TESTME - may be null
			} else if (n == null)
				n = NodeFactory.newComponent_UnTyped(mbr);
			linkMember(n);
			n.setLibrary(this);
		}
		if (!hasXsd)
			elementRoot = null;
	}

	private void generateLibrary(final XSDLibrary xLib) {
		elementRoot = new NavNode(ELEMENTS, this);
		if (genTLLib == null)
			makeGeneratedComponentLibrary(xLib);
		for (final LibraryMember mbr : xLib.getNamedMembers()) {
			Node n = GetNode(mbr); // use node if member is already modeled.
			if (n == null) {
				final XsdNode xn = new XsdNode(mbr, this);
				n = xn.getOtmModel();
				xn.setXsdType(true);
				if (n == null)
					LOGGER.debug("ERROR - null otm node.");
				else
					n.setXsdType(true); // FIXME
			} else
				LOGGER.debug("Used listener to get: " + n.getNameWithPrefix());
			if (n != null) {
				linkMember(n);
				n.setLibrary(this);
			}
		}
	}

	private void generateLibrary(final TLLibrary tlLib) {
		for (final LibraryMember mbr : tlLib.getNamedMembers()) {
			ComponentNode n = (ComponentNode) GetNode(mbr);
			// ComponentNode n = (ComponentNode) getNodeIfInThisLib(mbr);
			if (mbr instanceof TLService) {
				if (n instanceof ServiceNode)
					((ServiceNode) n).link((TLService) mbr, this);
				else
					new ServiceNode((TLService) mbr, this);
			} else if (mbr instanceof TLResource)
				if (n instanceof ResourceNode) {
					n.getLibrary().remove(n);
					this.linkMember(n);
				} else
					new ResourceNode((TLResource) mbr, this);
			else {
				// If the parent is a version aggregate (inChain) and the tlLib already has nodes associated, use those
				// node. Otherwise create new ones.
				if (n == null)
					n = NodeFactory.newComponent_UnTyped(mbr);
				// else
				// LOGGER.debug("Used listener to generate: " + n.getNameWithPrefix());
				linkMember(n);
				// done in linkMember() - n.setLibrary(this);
			}
		}
		new TypeResolver().resolveTypes(); // TODO - this is run too often
	}

	private ComponentNode getNodeIfInThisLib(LibraryMember mbr) {
		ComponentNode cn = (ComponentNode) GetNode(mbr);
		return cn != null && cn.getLibrary() != this ? null : cn;
	}

	public boolean hasGeneratedChildren() {
		return genTLLib.getNamedMembers().size() > 0 ? true : false;
	}

	/**
	 * Add the context of the node to this TL library if it does not already exist. For XSD Nodes, the namespace and
	 * prefix are used. For all other nodes, any context values they contain are copied.
	 */
	public void addContext(final Node n) {
		if (getTLLibrary() == null)
			return;
		if (n.isXsdType()) {
			if (getTLLibrary().getContextByApplicationContext(n.getNamespace()) == null) {
				final TLContext ctx = new TLContext();
				ctx.setApplicationContext(n.getNamespace());
				ctx.setContextId(n.getNamePrefix());
				if (ctx.getContextId().isEmpty())
					ctx.setContextId("Imported");
				getTLLibrary().addContext(ctx);
			}
		} else {
			List<TLContext> ctxList = n.getUsedContexts();
			if (ctxList == null)
				return;
			for (TLContext ctx : ctxList) {
				if (getTLLibrary().getContextByApplicationContext(ctx.getApplicationContext()) == null) {
					final TLContext nctx = new TLContext();
					nctx.setApplicationContext(ctx.getApplicationContext());
					nctx.setContextId(ctx.getContextId());
					getTLLibrary().addContext(nctx);
				}
			}
		}
	}

	/**
	 * Add node to this library. Links to library's complex/simple or element root. Adds underlying the TL object to
	 * this library's TLModel library. Handles adding nodes to chains. Adds context to the TL Model library if needed.
	 * Does not change type assignments.
	 * <p>
	 * Add to tlLibrary.addNamedMember() <br>
	 * linkMember() <br>
	 * getChain.add()
	 */
	public void addMember(final Node n) {
		if (!isEditable()) {
			LOGGER.warn("Tried to addMember() " + n + " to non-editable library " + this);
			return;
		}
		if (n == null || n.getTLModelObject() == null) {
			LOGGER.warn("Tried to addMember() a null member: " + n);
			return;
		}
		if (!(n.getTLModelObject() instanceof LibraryMember)) {
			LOGGER.warn("Tried to addMember() a non-library member: " + n);
			return;
		}

		// This code is only needed because of defect in XSD importer.
		if (n.getParent() != null && n.getParent().getChildren().contains(n)) {
			LOGGER.warn(n + " is already a child of its parent.");
			return;
		} else if ((n instanceof SimpleComponentInterface) && getSimpleRoot().getChildren().contains(n)) {
			LOGGER.warn(n + " is already a child of its parent.");
			return;
		} else if ((n instanceof ComplexComponentInterface) && getComplexRoot().getChildren().contains(n)) {
			LOGGER.warn(n + " is already a child of its parent.");
			return;
		}

		// If it is in a different library, remove it from that one.
		if (n.getLibrary() != null && n.getLibrary() != this)
			n.removeFromLibrary();

		// TL Library - Make sure the node's tl object is in the right tl library.
		LibraryMember tln = (LibraryMember) n.getTLModelObject(); // cast checked above
		if (tln.getOwningLibrary() == null)
			getTLLibrary().addNamedMember(tln);
		else if (tln.getOwningLibrary() != getTLLibrary()) {
			// LOGGER.debug("Moving " + n + " from " + tln.getOwningLibrary().getPrefix() + ":"
			// + tln.getOwningLibrary().getName() + " to " + getTLLibrary().getPrefix());
			tln.getOwningLibrary().removeNamedMember(tln);
			getTLLibrary().addNamedMember(tln);
		}

		// Add to this library child array
		if (linkMember(n)) {
			// If this library is in a chain, add the member to the chain's aggregates.
			if (isInChain())
				getChain().add((ComponentNode) n);
		}

		// Fail if in the list more than once.
		if (n.getParent().getChildren().indexOf(n) != n.getParent().getChildren().lastIndexOf(n))
			LOGGER.error(n + " is in list more than once.");
		// assert (n.getParent().getChildren().indexOf(n) == n.getParent().getChildren().lastIndexOf(n));

	}

	public boolean isInChain() {
		return getParent() instanceof VersionAggregateNode;
	}

	@Override
	public LibraryChainNode getChain() {
		if (getParent() == null || getParent().getParent() == null)
			return null;
		return getParent().getParent() instanceof LibraryChainNode ? (LibraryChainNode) getParent().getParent() : null;
	}

	/**
	 * Close the library. If this library is not open in other projects, close all its members. If it is open in other
	 * projects, just remove if from the project and make sure navNodes have valid parents. Remove library from project.
	 * Only closes this library not others in the chain.
	 */
	@Override
	public void close() {
		// If this library is not open in other projects remove all children, otherwise just close
		LibraryNode libInOtherProject = null;
		for (LibraryNode lib : ModelNode.getAllUserLibraries())
			if (lib != this && lib.getNameWithPrefix().equals(getNameWithPrefix())) {
				// Use the editable lib if available.
				if (libInOtherProject == null || lib.isEditable())
					libInOtherProject = lib;
			}

		close(libInOtherProject == null); // if null, unlink all members

		// Nodes representing named types are shared across libraries. (see generateLibrary())
		// Go through each NavNode and make sure those nodes point to the nav nodes in the other library.
		if (libInOtherProject != null) {
			for (Node navNode : getChildren()) {
				List<Node> objects = new ArrayList<Node>(navNode.getChildren());
				if (navNode instanceof ServiceNode || navNode instanceof ResourceNode)
					moveNamedType(navNode, libInOtherProject);
				else
					for (Node child : objects) {
						// Make sure child has a valid parent
						if (child.getParent() == navNode) {
							// If the node is wrapped in a version node, the version node needs to change.
							Node actualChild = child;
							if (child instanceof VersionNode)
								actualChild = ((VersionNode) child).getVersionedObject();
							if (actualChild instanceof ComplexComponentInterface) {
								moveNamedType(child, libInOtherProject.getComplexRoot());
							} else if (actualChild instanceof SimpleComponentInterface) {
								moveNamedType(child, libInOtherProject.getSimpleRoot());
							} else if (actualChild instanceof OperationNode) {
								// LOGGER.debug("What to do with operation? " + child);
								moveNamedType(child, libInOtherProject.getServiceRoot());
							} else if (actualChild instanceof ResourceMemberInterface)
								moveNamedType(child, libInOtherProject.getResourceRoot());
							// LOGGER.debug("What to do with resource? " + child);
							else
								LOGGER.debug("Unhandled child: " + child);
						}
						// Remove child from this libraries' navNodes.
						navNode.getChildren().remove(child);
					}
			}
		}

	}

	private void moveNamedType(Node child, Node newParent) {
		if (!newParent.getChildren().contains(child))
			newParent.getChildren().add(child);
		child.setParent(newParent);
		child.setLibrary(newParent.getLibrary());
	}

	/**
	 * Close library which removes it from the GUI but not OTM model. It must be the caller's responsibility to assure
	 * that the project state is saved.
	 * 
	 * node marked delete, parent and library set to null.
	 * 
	 * ProjectItem for this library is removed from the TL Project.
	 * 
	 * @param doMembers
	 *            - if true each child member is closed and contexts cleared. if false this node is removed from parent
	 */
	private void close(boolean doMembers) {
		if (isBuiltIn())
			return;
		if (isDeleted())
			return;

		Project project = getProject().getProject(); // do before unlinking

		if (doMembers) {
			// if (isInChain())
			// getChain().close();
			// else {
			List<Node> kids = new ArrayList<Node>(getChildren());
			for (Node kid : kids)
				kid.close();
			// super.close();
			// }
			// Remove context
			ContextController cc = OtmRegistry.getMainController().getContextController();
			cc.clearContexts(this);
		} // 7/2016 - dmh
			// else {
			// Unlink from tree
		if (getParent() != null && getParent().getChildren() != null)
			getParent().getChildren().remove(this);
		// }

		// Remove from containing TL (schema compiler/repository) project.
		project.remove(projectItem);

		deleted = true;
		setParent(null);
		setLibrary(null);

		// LOGGER.info("Closed library " + this);
		return;
	}

	/**
	 * Commit changes to the repository.
	 * 
	 * @throws RepositoryException
	 */
	public void commit() throws RepositoryException {
		projectItem.getProjectManager().commit(this.projectItem);
		// LOGGER.debug("Committed " + this);
	}

	@Override
	public void delete() {
		delete(true);
	}

	public void delete(boolean doMembers) {
		if (isBuiltIn())
			return;

		// Remove from project.
		if (getParent() instanceof ProjectNode) {
			ProjectNode pn = (ProjectNode) getParent();
			pn.getProject().remove(projectItem);
		}
		// Remove context
		ContextController cc = OtmRegistry.getMainController().getContextController();
		cc.clearContexts(this);

		// Now delete the nodes.
		if (doMembers)
			super.delete();
	}

	/**
	 * @return TypeProvider with matching name or null if not found.
	 */
	public TypeProvider findTypeProvider(String name) {
		TypeProvider node = null;
		for (TypeProvider p : getDescendants_TypeProviders())
			if (p.getName().equals(name))
				return p;
		return node;
	}

	/**
	 * Move a node from its library to a different library. Moves the node and underlying TL object.
	 * 
	 * @param source
	 * @param destination
	 */
	public boolean moveMember(final Node source, LibraryNode destination) throws IllegalArgumentException {
		if (source == null || source.getModelObject() == null || source.getTLModelObject() == null)
			throw new IllegalArgumentException("Null in move source model.");
		if (!(source instanceof ComponentNode))
			throw new IllegalArgumentException(source + " is not a component.");
		if (destination == null)
			throw new IllegalArgumentException("Move destination library is null.");
		if (!(destination.getTLaLib() instanceof TLLibrary))
			throw new IllegalArgumentException("Move destination library is not a TLLibrary.");

		// FIXME - this does not do services at all.
		// You can't move a service it one already exists in target library.
		// if (source.isService() && destination.hasService())
		if (source instanceof ServiceNode)
			return false;

		// Moved to LibraryNodeListener
		// Remove from source
		// if (isInChain()) {
		// getChain().removeAggregate((ComponentNode) source);
		// }
		// source.unlinkNode();
		addListeners();
		destination.addListeners();

		// Move the TL object to destination tl library.
		try {
			source.getLibrary()
					.getTLLibrary()
					.moveNamedMember((LibraryMember) source.getTLModelObject(), destination.getLibrary().getTLLibrary());
		} catch (Exception e) {
			// Failed to move. Change destination to be this library and relink.
			destination = this;
			LOGGER.debug("moveNamedMember failed. Adding back to " + this + " library.");
		}
		removeListeners();
		destination.removeListeners();

		destination.collapseContexts(); // reduce down to one context
		assert (destination.getTLLibrary().getContexts().size() == 1);

		// Add to destination library chain
		// TODO - should this use addMember() ?
		// destination.linkMember(source);
		// if (destination.isInChain()) {
		// destination.getChain().add((ComponentNode) source);
		// }
		return true;
	}

	/**
	 * Delete this library.
	 */

	/**
	 * Remove the node from its library. Remove from the library node and from the underlying tl library. Navigation and
	 * family nodes are NOT deleted. Does NOT delete the node or its TL Object contents. TL type assignments are assured
	 * to match the assignments in TypeNode.
	 * 
	 * NOTE - does not replace this node with an earlier version in a version chain.
	 * 
	 */
	protected void removeMember(final Node n) {
		if (n == null || n.getTLModelObject() == null) {
			LOGGER.warn("LibraryNode:removeMember() - error. model object or tl model object is null. " + n.getName()
					+ " - " + n.getClass().getSimpleName());
			return;
		}
		if (!(n.getTLModelObject() instanceof LibraryMember)) {
			LOGGER.warn("Tried to remove non-libraryMember: " + n);
			return;
		}

		n.unlinkNode();
		n.getLibrary().getTLLibrary().removeNamedMember((LibraryMember) n.getTLModelObject());
		n.setLibrary(null);
		// n.fixAssignments();
	}

	@Override
	public LibraryNode getLibrary() {
		return this;
	}

	public AbstractLibrary getTLaLib() {
		return absTLLibrary;
	}

	public Node getSimpleRoot() {
		return simpleRoot;
	}

	/**
	 * @return - Return the library's complex root node.
	 */
	public Node getComplexRoot() {
		return complexRoot;
	}

	public Node getServiceRoot() {
		return serviceRoot;
	}

	public Node getResourceRoot() {
		return resourceRoot;
	}

	@Override
	public String getComponentType() {
		return DEFAULT_LIBRARY_TYPE;
	}

	@Override
	public List<Node> getNavChildren() {
		return parent instanceof VersionAggregateNode ? new ArrayList<Node>() : getChildren();
	}

	@Override
	public String getNamespace() {
		// return emptyIfNull(getTLaLib().getNamespace());
		return modelObject.getNamespace(); // some libraries have empty mo
	}

	public String getNSBase() {
		return nsHandler.getNSBase(getNamespace());
	}

	public String getNSExtension() {
		return nsHandler.getNSExtension(getNamespace());
	}

	public String getNSVersion() {
		return nsHandler.getNSVersion(getNamespace());
	}

	@Override
	public String getNamespaceWithPrefix() {
		final String prefix = getNamePrefix();
		final String namespace = getNamespace();
		return (prefix.isEmpty() ? "() " + namespace : "( " + prefix + " ) " + namespace);
	}

	@Override
	public String getNamePrefix() {
		return emptyIfNull(getTLaLib().getPrefix());
	}

	@Override
	public boolean hasNavChildren() {
		if (parent instanceof VersionAggregateNode)
			return false;
		return getChildren().size() <= 0 ? false : true;
	}

	/**
	 * @return true if this library or one in its chain has a service.
	 */
	public boolean hasService() {
		boolean result = false;
		LibraryChainNode chain = getChain();
		if (chain == null) {
			// TODO - why not just check the service node?
			for (Node n : getChildren())
				if (n instanceof ServiceNode)
					result = true;
		} else
			result = !chain.getServiceAggregate().getChildren().isEmpty();
		return result;
	}

	private ServiceNode getService() {
		for (Node n : getChildren()) {
			if (n instanceof ServiceNode)
				return (ServiceNode) n;
		}
		return null;
	}

	private List<ResourceNode> getResources() {
		List<ResourceNode> resources = new ArrayList<ResourceNode>();
		for (Node n : getResourceRoot().getChildren()) {
			if (n instanceof ResourceNode)
				resources.add((ResourceNode) n);
		}
		return resources;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.opentravel.schemas.node.INode#hasChildren_TypeProviders()
	 */
	@Override
	public boolean hasChildren_TypeProviders() {
		return getChildren().size() > 0 ? true : false;
	}

	@Override
	public boolean isNavigation() {
		return true;
	}

	@Override
	public boolean isXSDSchema() {
		return getTLaLib() instanceof XSDLibrary;
	}

	/**
	 * Is this either of the built in libraries: XSD Schema or OTA_Common_v01_00
	 */
	@Override
	public boolean isBuiltIn() {
		return getTLaLib() instanceof BuiltInLibrary;
	}

	@Override
	public boolean isTLLibrary() {
		return getTLaLib() instanceof TLLibrary;
	}

	/**
	 * @throws IllegalArgumentException
	 *             - thrown if the new value creates a name/namespace conflict with another library
	 */
	@Override
	public void setName(final String n) {
		if (!this.isTLLibrary() || getTLaLib() == null)
			return;
		getTLaLib().setName(n);
		// Libraries do not use families...no need for super behavior.
		// LOGGER.debug("LibraryNode:setName() - name set to " + n);
	}

	@Override
	public String getName() {
		return emptyIfNull(getTLaLib().getName());
	}

	@Override
	public String getLabel() {
		String prefix = "";

		if (!getNamePrefix().isEmpty())
			prefix = getNamePrefix() + ":";

		return prefix + getName();
	}

	/**
	 * @return - true if members of the library can be moved
	 */
	public boolean isMoveable() {
		return isManaged() ? isEditable() : isTLLibrary();
	}

	/**
	 * Temporary library has a specific name
	 * 
	 * @deprecated
	 */
	@Deprecated
	protected boolean isTemporaryLibrary() {
		if (getName().equals(TempLib))
			return true;
		return false;
	}

	public void setNamespace(String ns) {
		if (nsHandler == null)
			throw new IllegalStateException("Null nsHandler");
		if (ns == null || ns.isEmpty())
			throw new IllegalArgumentException("Null or empty namespace argument.");
		String prefix = getNamePrefix(); // save in case not registered.
		nsHandler.setLibraryNamespace(this, ns);
		if (getNamePrefix().isEmpty()) {
			nsHandler.setNamespacePrefix(ns, prefix);
			setNSPrefix(prefix);
		}
		updateLibraryStatus();
	}

	public void setNSPrefix(String n) {
		if (n == null || n.isEmpty()) {
			n = UNDEFINED_PROPERTY_TXT;
		}
		getTLaLib().setPrefix(n);
		nsHandler.setNamespacePrefix(getNamespace(), n);
	}

	public void setVersionScheme(final String scheme) {
		if (absTLLibrary instanceof TLLibrary) {
			((TLLibrary) absTLLibrary).setVersionScheme(scheme);
		}
	}

	public String getVersionScheme() {
		String scheme = "";
		if (absTLLibrary instanceof TLLibrary) {
			scheme = ((TLLibrary) absTLLibrary).getVersionScheme();
		}
		return emptyIfNull(scheme);
	}

	public void setVersion(final String version) {
		if (absTLLibrary instanceof TLLibrary) {
			((TLLibrary) absTLLibrary).setVersion(version);
			// LOGGER.debug("Set version of " + this + " to " + version);
		}
		// TODO - implement the rest of the version logic!
	}

	public void markFinal() throws RepositoryException {
		if (absTLLibrary instanceof TLLibrary) {
			getProjectItem().getProjectManager().promote(projectItem);
			// LOGGER.debug("Set status of " + this + " to Final.");
		}
	}

	public String getVersion() {
		String version = "";
		if (absTLLibrary != null && absTLLibrary instanceof TLLibrary) {
			version = ((TLLibrary) absTLLibrary).getVersion();
		}
		return emptyIfNull(version);
	}

	public void setPath(final String text) {
		final File file = new File(text);
		final URL fileURL = URLUtils.toURL(file);
		// LOGGER.debug("File url being set to: "+fileURL);
		absTLLibrary.setLibraryUrl(fileURL);
	}

	/**
	 * @return the file path converted from the library URL
	 */
	public String getPath() {
		String path = "";
		if (absTLLibrary != null && absTLLibrary.getLibraryUrl() != null) {
			try {
				path = URLUtils.toFile(absTLLibrary.getLibraryUrl()).getCanonicalPath();
			} catch (final Exception e) {
				path = absTLLibrary.getLibraryUrl().toString();
			}
		}
		return emptyIfNull(path);
	}

	/**
	 * Get Repository
	 */
	public String getRepositoryDisplayName() {
		if (projectItem == null) {
			return "Error";
		}
		if (projectItem.getRepository() == null)
			return "Local File System";
		if (projectItem.getRepository() instanceof RemoteRepository)
			return ((RemoteRepository) projectItem.getRepository()).getDisplayName();
		else
			return "Local";
	}

	/**
	 * @return the projectItem
	 */
	public ProjectItem getProjectItem() {
		return projectItem;
	}

	/**
	 * Return the project containing this library or its chain. Null if no project is found.
	 * 
	 * @return
	 */
	public ProjectNode getProject() {
		if (getParent() instanceof ProjectNode)
			return (ProjectNode) getParent();
		else if (getParent() instanceof NavNode)
			return (ProjectNode) getParent().getParent().getParent();
		else
			return null;
	}

	/**
	 * @param projectItem
	 *            the projectItem to set
	 */
	public void setProjectItem(ProjectItem projectItem) {
		this.projectItem = projectItem;
	}

	public void setComments(final String comments) {
		if (absTLLibrary instanceof TLLibrary) {
			((TLLibrary) absTLLibrary).setComments(comments);
		}
	}

	public String getComments() {
		String comments = "";
		if (absTLLibrary instanceof TLLibrary) {
			comments = ((TLLibrary) absTLLibrary).getComments();
		}
		return emptyIfNull(comments);
	}

	/**
	 * Note - locally defined types are not returned, only those with public names. For XSD nodes, the OTM model node is
	 * returned
	 * 
	 * @return list of all named simple types in this library.
	 */
	// FIXME - ONLY used in tests. move or remove
	public List<SimpleTypeNode> getNamedSimpleTypes() {
		return (getNamedSimpleTypes(simpleRoot));
	}

	private List<SimpleTypeNode> getNamedSimpleTypes(INode n) {
		ArrayList<SimpleTypeNode> namedKids = new ArrayList<SimpleTypeNode>();
		for (Node c : n.getChildren()) {
			// has simple, enum, family and xsd nodes in the list.
			if (c.isTypeProvider()) {
				if (c instanceof SimpleTypeNode)
					namedKids.add((SimpleTypeNode) c);
				else if (c instanceof XsdNode && c.isSimpleType())
					namedKids.add((SimpleTypeNode) ((XsdNode) c).getOtmModel());
			} else if (c.isNavigation())
				namedKids.addAll(getNamedSimpleTypes(c));
		}
		return namedKids;
	}

	/**
	 * @return - list of context id strings, empty list of not TLLibrary or no contexts assigned.
	 */
	// FIXME - only used in tests
	public List<String> getContextIds() {
		ArrayList<String> contexts = new ArrayList<String>();
		for (TLContext c : getTLLibrary().getContexts())
			contexts.add(c.getContextId());
		return contexts;
	}

	/**
	 * Get the default context. As of 9/2015 there is only one context. This method allows for the obsolescence of the
	 * context controller.
	 */
	public String getDefaultContextId() {
		String id = OtmRegistry.getMainController().getContextController().getDefaultContextId(this);
		if (id.isEmpty() && absTLLibrary instanceof TLLibrary)
			if (getTLLibrary().getContexts().get(0) instanceof TLContext)
				id = getTLLibrary().getContexts().get(0).getContextId();
		return id;
	}

	/**
	 * 
	 * @return - Return either the original TLLibrary or the one used for generated components.
	 */
	public TLLibrary getTLLibrary() {
		return absTLLibrary instanceof TLLibrary ? (TLLibrary) absTLLibrary : genTLLib;
	}

	/**
	 * Set the current default context
	 * 
	 * @param curContext
	 */
	public void setCurContext(String curContext) {
		this.curContext = curContext;
	}

	private static String emptyIfNull(final String string) {
		return string == null ? "" : string;
	}

	@Override
	public boolean isDeleted() {
		return deleted;
	}

	@Override
	public boolean isDeleteable() {
		return true;
	}

	/**
	 * @return - returns the string if not null or empty, or else the ifTrue string
	 */
	private static String defautIfNullOrEmpty(String s, String ifTrue) {
		return (s == null) || s.isEmpty() ? ifTrue : s;
	}

	/**
	 * Add a local member to the library. Local members are not Named Types. Local members represent local anonymous
	 * types from an XSD schema type.
	 * 
	 * @param n
	 */
	// TODO - move the temp/local library management here. genTLLib
	public void addLocalMember(XsdNode xn, ComponentNode cn) {
		if (xn == null || cn == null)
			return;
		cn.xsdNode = xn;
		cn.setLibrary(this);
		cn.xsdType = true;
		cn.local = true;
		xn.otmModel = cn;

		if (xn.getParent() != null) {
			xn.unlinkNode();
			// LOGGER.debug("HUMM...why was this linked?");
		}
		linkMember(cn);
	}

	/**
	 * Get all type providers within library. Includes simple and complex objects, aliases and facets. Does NOT return
	 * any local-anonymous types.
	 * 
	 * @return
	 */
	// FIXME - this method also is in Node
	public List<Node> getDescendentsNamedTypeProviders() {
		ArrayList<Node> namedTypeProviders = new ArrayList<Node>();
		for (Node n : getChildren())
			namedTypeProviders.addAll(gntp(n));
		return namedTypeProviders;
	}

	private Collection<? extends Node> gntp(Node n) {
		ArrayList<Node> lst = new ArrayList<Node>();
		if (n.isTypeProvider() && !isLocal())
			lst.add(n);
		for (Node gc : n.getChildren())
			lst.addAll(gntp(gc));
		return lst;
	}

	/**
	 * Get a list of libraries that contain types assigned to any type user in this library.
	 */
	public List<LibraryNode> getAssignedLibraries() {
		// Walk selected library type users and collect all used libraries (type assignments and extensions)
		List<LibraryNode> usedLibs = new ArrayList<LibraryNode>();
		for (TypeUser user : getDescendants_TypeUsers()) {
			TypeProvider provider = user.getAssignedType();
			if (provider != null && provider.getLibrary() != null && provider.getLibrary().getChain() != null)
				if (provider.getLibrary() != this && !usedLibs.contains(provider.getLibrary()))
					usedLibs.add(provider.getLibrary());
		}
		return usedLibs;
	}

	/**
	 * Get all type providers within library. Includes simple and complex objects only. Does NOT return any
	 * local-anonymous types. // FIXME - this method also is in Node. One in node does not include services.
	 * 
	 * @return
	 */
	@Deprecated
	public List<Node> getDescendentsNamedTypes() {
		ArrayList<Node> namedTypeProviders = new ArrayList<Node>();
		for (Node n : getChildren())
			namedTypeProviders.addAll(gnt(n));
		return namedTypeProviders;
	}

	private Collection<? extends Node> gnt(Node n) {
		ArrayList<Node> list = new ArrayList<Node>();
		if (n.isNamedType() && !isLocal())
			list.add(n);
		for (Node gc : n.getChildren())
			list.addAll(gnt(gc));
		return list;
	}

	/**
	 * Creates a new library from the library with version incremented. Returns the new library.
	 * 
	 * @param project
	 * @return
	 */

	/**
	 * Is the library ready to version?
	 */
	public boolean isReadyToVersion() {
		// LOGGER.debug("Ready to version? valid: " + isValid() + ", managed: " + isManaged());
		return isManaged() && isValid();
	}

	/** ***************************** Library Status ************************* **/

	/**
	 * Get the editing status of the library.
	 */
	@Override
	public NodeEditStatus getEditStatus() {
		NodeEditStatus status = NodeEditStatus.FULL;
		if (GeneralPreferencePage.areNamespacesManaged() && !isInProjectNS())
			status = NodeEditStatus.NOT_EDITABLE;
		else if (isManaged()) {
			if (!isLocked())
				status = NodeEditStatus.MANAGED_READONLY;
			else if (isMajorVersion())
				status = NodeEditStatus.FULL;
			else if (isMinorOrMajorVersion())
				status = NodeEditStatus.MINOR;
			else
				status = NodeEditStatus.PATCH;
		}
		return status;
	}

	// @Override
	// public String getEditStatusMsg() {
	// // LOGGER.debug(this + " library status = " + getEditStatus().msgID());
	// return Messages.getString(getEditStatus().msgID());
	// }

	/**
	 * @return true if this library is managed in a repository.
	 */
	public boolean isManaged() {
		if (isBuiltIn())
			return false;
		return projectItem != null && !projectItem.getState().equals(RepositoryItemState.UNMANAGED);
	}

	/**
	 * @return true if this library is managed and locked in a repository.
	 */
	public boolean isLocked() {
		return projectItem != null
				&& (projectItem.getState().equals(RepositoryItemState.MANAGED_LOCKED) || projectItem.getState().equals(
						RepositoryItemState.MANAGED_WIP));
	}

	/**
	 * @return true if this library's namespace is within the project's namespace.
	 */
	public boolean isInProjectNS() {
		// String projectNS = "ZZZZZXXXXCCCCVVVVV"; // never found!
		// if (getProject() != null)
		// projectNS = getProject().getNamespace();
		// return getNamespace().startsWith(projectNS);
		return getProject() != null ? getNamespace().startsWith(getProject().getNamespace()) : false;
	}

	/**
	 * @return true if this library is a major version
	 */
	public boolean isMajorVersion() {
		return nsHandler.getNS_Minor(getNamespace()).equals("0") && nsHandler.getNS_Patch(getNamespace()).equals("0");
	}

	/**
	 * NOTE: Major versions will also return true as needed to create a minor from a major version.
	 * 
	 * @return true if this library is a major or minor version
	 */
	public boolean isMinorOrMajorVersion() {
		// return !nsHandler.getNS_Minor(getNamespace()).equals("0")
		// && nsHandler.getNS_Patch(getNamespace()).equals("0");
		return nsHandler.getNS_Patch(getNamespace()).equals("0");
	}

	/**
	 * Use library namespace to determine if it is a minor version.
	 * 
	 * @return true only if this library is a minor version
	 */
	public boolean isMinorVersion() {
		return !nsHandler.getNS_Minor(getNamespace()).equals("0") && nsHandler.getNS_Patch(getNamespace()).equals("0");
	}

	/**
	 * @return true if this library is a patch version
	 */
	public boolean isPatchVersion() {
		return !nsHandler.getNS_Patch(getNamespace()).equals("0");
	}

	@Override
	public String toString() {
		return getLabel();
	}

	/**
	 * Replace all type users in this library using the passed map. The map must contain key/value pairs of library
	 * nodes where the currently used library is the key. No action taken on types in libraries not in the map.
	 */
	public void replaceTypeUsers(HashMap<LibraryNode, LibraryNode> replacementMap) {
		TypeProvider provider = null;
		for (TypeUser user : getDescendants_TypeUsers()) {
			LibraryNode replacementLib = replacementMap.get(user.getAssignedType().getLibrary());
			if (user.isEditable() && replacementLib != null) {
				provider = replacementLib.findTypeProvider(user.getAssignedType().getName());
				if (provider != null)
					user.setAssignedType(provider); // don't set to null as null clears assignment
			}
		}
	}

	/**
	 * Done by the service node constructor. No need to do it anywhere else.
	 */
	protected void setServiceRoot(ServiceNode serviceNode) {
		serviceRoot = serviceNode;
	}

	/**
	 * 
	 */
	protected void setResourceRoot(NavNode resourceNode) {
		this.resourceRoot = resourceNode;
	}

	public void setAsDefault() {
		this.getProject().getProject().setDefaultItem(getProjectItem());
	}

}
