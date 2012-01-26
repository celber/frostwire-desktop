package com.frostwire.gui.library;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.frostwire.alexandria.InternetRadioStation;
import com.frostwire.alexandria.Playlist;
import com.frostwire.gui.bittorrent.TorrentUtil;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.gui.options.ConfigureOptionsAction;
import com.limegroup.gnutella.gui.options.OptionsConstructor;
import com.limegroup.gnutella.gui.search.NamedMediaType;
import com.limegroup.gnutella.gui.tables.DefaultMouseListener;
import com.limegroup.gnutella.gui.themes.SkinMenuItem;
import com.limegroup.gnutella.gui.themes.SkinPopupMenu;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.settings.LibrarySettings;
import com.limegroup.gnutella.settings.SharingSettings;

public class LibraryExplorer extends AbstractLibraryListPanel {

    private static final long serialVersionUID = -5354238047697767760L;

    private DefaultTreeModel model;
    private JTree tree;

    private TextNode root;
    private DevicesNode devicesNode;

    private Action refreshAction = new RefreshAction();
    private Action exploreAction = new ExploreAction();

    private TreeSelectionListener treeSelectionListener;

    public LibraryExplorer() {
        setupUI();
    }

    @Override
    public void refresh() {
        tree.repaint();
    }

    public void handleDeviceNew(Device device) {
        DeviceNode deviceNode = new DeviceNode(device);
        devicesNode.add(deviceNode);
        model.insertNodeInto(deviceNode, devicesNode, devicesNode.getChildCount() - 1);
        tree.expandPath(new TreePath(devicesNode.getPath()));

        System.out.println("New Device: " + device);

        refreshDeviceNode(device);
    }

    public void handleDeviceAlive(Device device) {
        refreshDeviceNode(device);
    }

    public void handleDeviceStale(Device device) {
        DeviceNode node = findNode(device);
        model.removeNodeFromParent(node);
    }

    public void refreshSelection(boolean clearCache) {
        LibraryNode node = (LibraryNode) tree.getLastSelectedPathComponent();

        if (node == null) {
            return;
        }

        if (node instanceof DeviceFileTypeTreeNode) {
            DeviceFileTypeTreeNode deviceFileTypeNode = (DeviceFileTypeTreeNode) node;
            LibraryMediator.instance().updateTableFiles(deviceFileTypeNode.getDevice(), deviceFileTypeNode.getFileType());
        } else {
            LibraryMediator.instance().clearLibraryTable();
        }

        DirectoryHolder directoryHolder = getSelectedDirectoryHolder();

        //STARRED
        if (directoryHolder instanceof StarredDirectoryHolder) {
            Playlist playlist = LibraryMediator.getLibrary().getStarredPlaylist();
            LibraryMediator.instance().updateTableItems(playlist);
            String status = LibraryUtils.getPlaylistDurationInDDHHMMSS(playlist) + ", " + playlist.getItems().size() + " " + I18n.tr("tracks");
            LibraryMediator.instance().getLibrarySearch().setStatus(status);

        }
        //RADIO
        else if (directoryHolder instanceof InternetRadioDirectoryHolder) {

            List<InternetRadioStation> internetRadioStations = LibraryMediator.getLibrary().getInternetRadioStations();
            LibraryMediator.instance().showInternetRadioStations(internetRadioStations);
        }
        //TORRENTS
        else if (directoryHolder instanceof TorrentDirectoryHolder) {
            LibraryMediator.instance().updateTableFiles(directoryHolder);
        }
        //FINISHED
        else if (directoryHolder instanceof SavedFilesDirectoryHolder) {
            if (clearCache) {
                ((SavedFilesDirectoryHolder) directoryHolder).clearCache();
            }
            LibraryMediator.instance().updateTableFiles(directoryHolder);
        }
        //MEDIA TYPES
        else if (directoryHolder instanceof MediaTypeSavedFilesDirectoryHolder) {
            MediaTypeSavedFilesDirectoryHolder mtsfdh = (MediaTypeSavedFilesDirectoryHolder) directoryHolder;
            if (clearCache) {
                mtsfdh.clearCache();
            }

            LibraryMediator.instance().updateTableFiles(directoryHolder);

            BackgroundExecutorService.schedule(new SearchByMediaTypeRunnable(mtsfdh));

        }

        LibraryMediator.instance().getLibrarySearch().clear();
    }

    protected void setupUI() {
        setLayout(new BorderLayout());
        GUIMediator.addRefreshListener(this);

        setupModel();
        setupTree();

        add(new JScrollPane(tree));
    }

    private void setupModel() {
        root = new TextNode("root");

        addNodesPerMediaType(root);

        root.add(new DirectoryHolderNode(new InternetRadioDirectoryHolder()));
        root.add(new DirectoryHolderNode(new StarredDirectoryHolder()));
        root.add(new DirectoryHolderNode(new TorrentDirectoryHolder()));
        root.add(new DirectoryHolderNode(new SavedFilesDirectoryHolder(SharingSettings.TORRENT_DATA_DIR_SETTING, I18n.tr("Finished Downloads"))));

        devicesNode = new DevicesNode(I18n.tr("Devices"));
        root.add(devicesNode);

        model = new DefaultTreeModel(root);
    }

    private void setupTree() {
        tree = new LibraryIconTree(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(false);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new NodeRenderer());
        tree.setDragEnabled(true);
        tree.setTransferHandler(new LibraryFilesTransferHandler(null));
        ((BasicTreeUI)tree.getUI()).setExpandedIcon(null);
        ((BasicTreeUI)tree.getUI()).setCollapsedIcon(null);

        SkinPopupMenu popup = new SkinPopupMenu();
        popup.add(new SkinMenuItem(refreshAction));
        popup.add(new SkinMenuItem(exploreAction));
        popup.add(new SkinMenuItem(new ConfigureOptionsAction(OptionsConstructor.SHARED_KEY, I18n.tr("Configure Options"), I18n.tr("You can configure the FrostWire\'s Options."))));
        tree.addMouseListener(new DefaultMouseListener(new TreeMouseObserver(tree, popup)));

        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (LibraryUtils.isRefreshKeyEvent(e)) {
                    refreshSelection(true);
                }
            }
        });

        treeSelectionListener = new LibraryExplorerTreeSelectionListener();
        tree.addTreeSelectionListener(treeSelectionListener);

        ToolTipManager.sharedInstance().registerComponent(tree);
    }

    private void addNodesPerMediaType(DefaultMutableTreeNode root) {
        addNodePerMediaType(root, NamedMediaType.getFromMediaType(MediaType.getAudioMediaType()));
        addNodePerMediaType(root, NamedMediaType.getFromMediaType(MediaType.getVideoMediaType()));
        addNodePerMediaType(root, NamedMediaType.getFromMediaType(MediaType.getImageMediaType()));
        addNodePerMediaType(root, NamedMediaType.getFromMediaType(MediaType.getProgramMediaType()));
        addNodePerMediaType(root, NamedMediaType.getFromMediaType(MediaType.getDocumentMediaType()));
    }

    private void addNodePerMediaType(DefaultMutableTreeNode root, NamedMediaType nm) {
        DirectoryHolder directoryHolder = new MediaTypeSavedFilesDirectoryHolder(nm.getMediaType());
        DirectoryHolderNode node = new DirectoryHolderNode(directoryHolder);
        root.add(node);
    }

    private DeviceNode findNode(Device device) {
        DeviceNode deviceNode = null;

        for (int i = 0; i < devicesNode.getChildCount() && deviceNode == null; i++) {
            TreeNode node = devicesNode.getChildAt(i);
            if (node instanceof DeviceNode) {
                if (((DeviceNode) node).getDevice().equals(device)) {
                    deviceNode = (DeviceNode) node;
                }
            }
        }

        return deviceNode;
    }

    private DeviceFileTypeTreeNode findNode(DeviceNode deviceNode, byte fileType) {
        DeviceFileTypeTreeNode deviceFileTypeNode = null;

        for (int i = 0; i < deviceNode.getChildCount() && deviceFileTypeNode == null; i++) {
            TreeNode node = deviceNode.getChildAt(i);
            if (node instanceof DeviceFileTypeTreeNode) {
                if (((DeviceFileTypeTreeNode) node).getFileType() == fileType) {
                    deviceFileTypeNode = (DeviceFileTypeTreeNode) node;
                }
            }
        }

        return deviceFileTypeNode;
    }

    private void refreshDeviceNode(Device device) {
        DeviceNode node = findNode(device);

        if (node == null) {
            return; // weird case, no need to do anything
        }

        refreshDeviceFileTypeNode(node, DeviceConstants.FILE_TYPE_AUDIO);
        refreshDeviceFileTypeNode(node, DeviceConstants.FILE_TYPE_PICTURES);
        refreshDeviceFileTypeNode(node, DeviceConstants.FILE_TYPE_VIDEOS);
        refreshDeviceFileTypeNode(node, DeviceConstants.FILE_TYPE_DOCUMENTS);
        refreshDeviceFileTypeNode(node, DeviceConstants.FILE_TYPE_APPLICATIONS);
        refreshDeviceFileTypeNode(node, DeviceConstants.FILE_TYPE_RINGTONES);
    }

    private void refreshDeviceFileTypeNode(DeviceNode deviceNode, byte fileType) {
        DeviceFileTypeTreeNode node = findNode(deviceNode, fileType);

        if (node == null) {
            if (UITool.getNumSharedFiles(deviceNode.getDevice().getFinger(), fileType) > 0) {
                node = new DeviceFileTypeTreeNode(deviceNode.getDevice(), fileType);
                model.insertNodeInto(node, deviceNode, 0);
                tree.expandPath(new TreePath(deviceNode.getPath()));
            }
        } else {
            if (UITool.getNumSharedFiles(deviceNode.getDevice().getFinger(), fileType) == 0) {
                model.removeNodeFromParent(node);
            } else {
                node.updateText();
            }
        }
    }

    public DirectoryHolder getSelectedDirectoryHolder() {
        LibraryNode node = (LibraryNode) tree.getLastSelectedPathComponent();
        return node != null && node instanceof DirectoryHolderNode ? ((DirectoryHolderNode) node).getDirectoryHolder() : null;
    }

    public Dimension getRowDimension() {
        Rectangle rect = tree.getUI().getPathBounds(tree, new TreePath(root));
        return rect.getSize();
    }

    public int getRowsCount() {
        return tree.getUI().getRowCount(tree);
    }

    public void clearSelection() {
        tree.clearSelection();
    }

    public void refreshSelection() {
        refreshSelection(false);
    }

    private class LibraryExplorerTreeSelectionListener implements TreeSelectionListener {
        public void valueChanged(TreeSelectionEvent e) {
            TreeNode node = (TreeNode) tree.getLastSelectedPathComponent();

            if (node == null) {
                return;
            }

            LibraryMediator.instance().getLibraryPlaylists().clearSelection();

            refreshSelection(false);
        }
    }

    private final class SearchByMediaTypeRunnable implements Runnable {

        private final MediaTypeSavedFilesDirectoryHolder _mtsfdh;

        public SearchByMediaTypeRunnable(MediaTypeSavedFilesDirectoryHolder mtsfdh) {
            _mtsfdh = mtsfdh;
        }

        public void run() {
            GUIMediator.safeInvokeLater(new Runnable() {
                public void run() {
                    LibraryMediator.instance().clearLibraryTable();
                }
            });

            final List<File> cache = new ArrayList<File>(_mtsfdh.getCache());
            if (cache.size() == 0) {

                File torrentDataDirFile = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();

                Set<File> ignore = TorrentUtil.getIgnorableFiles();

                Set<File> directories = new HashSet<File>(LibrarySettings.DIRECTORIES_TO_INCLUDE.getValue());
                directories.removeAll(LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());

                for (File dir : directories) {
                    if (dir == null) {
                        continue;
                    }
                    if (dir.equals(torrentDataDirFile)) {
                        search(dir, ignore, LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
                    } else if (dir.equals(LibrarySettings.USER_MUSIC_FOLDER) && !_mtsfdh.getMediaType().equals(MediaType.getAudioMediaType())) {
                        continue;
                    } else {
                        search(dir, new HashSet<File>(), LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
                    }
                }
            } else {
                GUIMediator.safeInvokeLater(new Runnable() {
                    public void run() {
                        LibraryMediator.instance().addFilesToLibraryTable(cache);
                    }
                });
            }

            LibraryExplorer.this.executePendingRunnables();
        }

        private void search(File file, Set<File> ignore, Set<File> exludedSubFolders) {

            if (file == null || !file.isDirectory() || !file.exists()) {
                return;
            }

            List<File> directories = new ArrayList<File>();
            final List<File> files = new ArrayList<File>();

            for (File child : file.listFiles()) {

                DirectoryHolder directoryHolder = getSelectedDirectoryHolder();
                if (!_mtsfdh.equals(directoryHolder)) {
                    return;
                }

                if (ignore.contains(child)) {
                    continue;
                }

                if (child.isHidden()) {
                    continue;
                }

                if (child.isDirectory() && !exludedSubFolders.contains(child)) {
                    directories.add(child);
                } else if (_mtsfdh.accept(child)) {
                    files.add(child);
                }
            }
            _mtsfdh.addToCache(files);
            Runnable r = new Runnable() {
                public void run() {
                    LibraryMediator.instance().addFilesToLibraryTable(files);
                }
            };

            GUIMediator.safeInvokeLater(r);

            for (File directory : directories) {
                search(directory, ignore, exludedSubFolders);
            }
        }
    }

    public void selectAudio() {
        LibraryNode selectedValue = (LibraryNode) tree.getLastSelectedPathComponent();
        if (selectedValue != null && selectedValue instanceof DirectoryHolderNode && ((DirectoryHolderNode) selectedValue).getDirectoryHolder() instanceof MediaTypeSavedFilesDirectoryHolder
                && ((MediaTypeSavedFilesDirectoryHolder) ((DirectoryHolderNode) selectedValue).getDirectoryHolder()).getMediaType().equals(MediaType.getAudioMediaType())) {
            // already selected
            try {
                treeSelectionListener.valueChanged(null);
            } catch (Exception e) {
                System.out.println();
            }
            return;
        }

        Enumeration<?> e = root.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            LibraryNode node = (LibraryNode) e.nextElement();
            if (node instanceof DirectoryHolderNode) {
                DirectoryHolder holder = ((DirectoryHolderNode) node).getDirectoryHolder();
                if (holder instanceof MediaTypeSavedFilesDirectoryHolder && ((MediaTypeSavedFilesDirectoryHolder) holder).getMediaType().equals(MediaType.getAudioMediaType())) {
                    tree.setSelectionPath(new TreePath(node.getPath()));
                    tree.scrollPathToVisible(new TreePath(node.getPath()));
                    return;
                }
            }
        }
    }

    public void selectStarred() {
        try {
            if (selectionListenerForSameItem(StarredDirectoryHolder.class)) {
                return;
            }

            Enumeration<?> e = root.depthFirstEnumeration();
            while (e.hasMoreElements()) {
                LibraryNode node = (LibraryNode) e.nextElement();
                if (node instanceof DirectoryHolderNode) {
                    DirectoryHolder holder = ((DirectoryHolderNode) node).getDirectoryHolder();
                    if (holder instanceof StarredDirectoryHolder) {
                        tree.setSelectionPath(new TreePath(node.getPath()));
                        tree.scrollPathToVisible(new TreePath(node.getPath()));
                        return;
                    }
                }
            }
        } finally {
            executePendingRunnables();
        }
    }
    
    public void selectFinishedDownloads() {
        try {
            if (selectionListenerForSameItem(StarredDirectoryHolder.class)) {
                return;
            }

            Enumeration<?> e = root.depthFirstEnumeration();
            while (e.hasMoreElements()) {
                LibraryNode node = (LibraryNode) e.nextElement();
                if (node instanceof DirectoryHolderNode) {
                    DirectoryHolder holder = ((DirectoryHolderNode) node).getDirectoryHolder();
                    if (holder instanceof SavedFilesDirectoryHolder) {
                        tree.setSelectionPath(new TreePath(node.getPath()));
                        tree.scrollPathToVisible(new TreePath(node.getPath()));
                        return;
                    }
                }
            }
        } finally {
            executePendingRunnables();
        }
    }

    public boolean selectionListenerForSameItem(Class<?> clazz) {
        LibraryNode selectedValue = (LibraryNode) tree.getLastSelectedPathComponent();

        if (selectedValue != null && selectedValue instanceof DirectoryHolderNode && clazz.isInstance(((DirectoryHolderNode) selectedValue).getDirectoryHolder())) {
            // already selected
            try {
                treeSelectionListener.valueChanged(null);
            } catch (Exception e) {
                System.out.println();
            }
            return true;
        }
        return false;
    }

    public void selectRadio() {
        try {
            if (selectionListenerForSameItem(InternetRadioDirectoryHolder.class)) {
                return;
            }

            Enumeration<?> e = root.depthFirstEnumeration();
            while (e.hasMoreElements()) {
                LibraryNode node = (LibraryNode) e.nextElement();
                if (node instanceof DirectoryHolderNode) {
                    DirectoryHolder holder = ((DirectoryHolderNode) node).getDirectoryHolder();
                    if (holder instanceof InternetRadioDirectoryHolder) {
                        tree.setSelectionPath(new TreePath(node.getPath()));
                        tree.scrollPathToVisible(new TreePath(node.getPath()));
                        return;
                    }
                }
            }
        } finally {
            executePendingRunnables();
        }
    }

    public List<MediaTypeSavedFilesDirectoryHolder> getMediaTypeSavedFilesDirectoryHolders() {
        List<MediaTypeSavedFilesDirectoryHolder> holders = new ArrayList<MediaTypeSavedFilesDirectoryHolder>();
        Enumeration<?> e = root.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            LibraryNode node = (LibraryNode) e.nextElement();
            if (node instanceof DirectoryHolderNode) {
                DirectoryHolder holder = ((DirectoryHolderNode) node).getDirectoryHolder();
                if (holder instanceof MediaTypeSavedFilesDirectoryHolder) {
                    holders.add((MediaTypeSavedFilesDirectoryHolder) holder);
                }
            }
        }
        return holders;
    }

    /**
     * Cleans the caches of all directory holders and refreshes the current selection.
     */
    public void clearDirectoryHolderCaches() {
        Enumeration<?> e = root.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            LibraryNode node = (LibraryNode) e.nextElement();
            if (node instanceof DirectoryHolderNode) {
                DirectoryHolder holder = ((DirectoryHolderNode) node).getDirectoryHolder();
                if (holder instanceof MediaTypeSavedFilesDirectoryHolder) {
                    ((MediaTypeSavedFilesDirectoryHolder) holder).clearCache();
                } else if (holder instanceof SavedFilesDirectoryHolder) {
                    ((SavedFilesDirectoryHolder) holder).clearCache();
                }
            }
        }

        refreshSelection();
    }

    private class RefreshAction extends AbstractAction {

        /**
         * 
         */
        private static final long serialVersionUID = 412879927060208864L;

        public RefreshAction() {
            putValue(Action.NAME, I18n.tr("Refresh"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Refresh selected"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_REFRESH");
        }

        public void actionPerformed(ActionEvent e) {
            DirectoryHolder directoryHolder = getSelectedDirectoryHolder();
            if (directoryHolder == null) {
                return;
            }
            refreshSelection(true);
        }
    }

    private class ExploreAction extends AbstractAction {

        /**
         * 
         */
        private static final long serialVersionUID = 2767346265174793478L;

        public ExploreAction() {
            putValue(Action.NAME, I18n.tr("Explore"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Open Library Folder"));
            putValue(LimeAction.ICON_NAME, "LIBRARY_EXPLORE");
        }

        public void actionPerformed(ActionEvent e) {
            DirectoryHolder directoryHolder = getSelectedDirectoryHolder();
            if (directoryHolder == null) {
                return;
            }
            File directory = directoryHolder.getDirectory();
            if (directory != null) {
                GUIMediator.launchExplorer(directory);
            }
        }
    }
}
