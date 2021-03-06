// This file is part of MongoFX.
//
// MongoFX is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
// MongoFX is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with MongoFX.  If not, see <http://www.gnu.org/licenses/>.

//
// Copyright (c) Andrey Dubravin, 2015
//
package mongofx.ui.dbtree;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javafx.scene.control.*;
import mongofx.ui.main.UIBuilder;
import mongofx.ui.msg.PopupService;
import org.bson.Document;

import com.google.inject.Inject;
import com.mongodb.MongoException;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.event.ActionEvent;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.MouseEvent;
import mongofx.service.Executor;
import mongofx.service.MongoConnection;
import mongofx.service.MongoDatabase;
import mongofx.service.MongoService.MongoDbConnection;
import mongofx.ui.dbtree.DbTreeValue.TreeValueType;
import mongofx.ui.main.MainFrameController;

public class DBTreeController {

  @Inject
  private Executor executor;

  @Inject
  private PopupService popupService;

  @Inject
  private UIBuilder uiBuilder;

  private TreeView<DbTreeValue> treeView;
  private MainFrameController mainFrameController;

  private ContextMenu dbContextMenu;
  private ContextMenu connectContextMenu;
  private ContextMenu collectionContextMenu;
  private ContextMenu indexContextMenu;

  public void reloadSelectedTreeItem() {
    TreeItem<DbTreeValue> selectedItem = treeView.getSelectionModel().getSelectedItem();
    findParentOfType(selectedItem, DynamicTreeItem.class).ifPresent(ti -> ti.reload());
  }

  private Optional<DynamicTreeItem> findParentOfType(TreeItem<DbTreeValue> selectedItem, Class<DynamicTreeItem> class1) {
    if (selectedItem == null) {
      return Optional.empty();
    }
    if (class1.isAssignableFrom(selectedItem.getClass())) {
      return Optional.of((DynamicTreeItem)selectedItem);
    }
    return findParentOfType(selectedItem.getParent(), class1);
  }

  private List<TreeItem<DbTreeValue>> buildDbList(MongoConnection dbConnect) {
    try {
      return dbConnect.listDbs().stream().map(d -> createDbItem(d)).collect(Collectors.toList());
    }
    catch (MongoException ex) {
      dbConnect.close();
      throw ex;
    }
  }

  private TreeItem<DbTreeValue> createDbItem(MongoDatabase d) {
    return new DynamicTreeItem(new DbTreeValue(d, d.getName(), TreeValueType.DATABASE),
        new FontAwesomeIconView(FontAwesomeIcon.DATABASE), executor, popupService, this::buildDbChilds);
  }

  private List<TreeItem<DbTreeValue>> buildDbChilds(DbTreeValue value) {
    MongoDatabase db = value.getMongoDatabase();
    return db.listCollectionDetails().stream()
        .map(cd -> new TreeItem<>(new DbTreeValue(db, cd, TreeValueType.COLLECTION),
            new FontAwesomeIconView(FontAwesomeIcon.TABLE)))
        .peek(ti -> buildCollectionDetail(db, ti)).collect(Collectors.toList());
  }

  private void buildCollectionDetail(MongoDatabase db, TreeItem<DbTreeValue> ti) {
    DbTreeValue indexCategory = new DbTreeValue(db, "Indexes", TreeValueType.CATEGORY);
    indexCategory.setCollectionName(ti.getValue().getDisplayValue());
    ti.getChildren().add(new DynamicTreeItem(indexCategory, new FontAwesomeIconView(FontAwesomeIcon.FOLDER), executor, popupService,
        this::buildIndexes));
  }

  private List<TreeItem<DbTreeValue>> buildIndexes(DbTreeValue value) {
    MongoCollection<Document> collection =
        value.getMongoDatabase().getMongoDb().getCollection(value.getCollectionName());

    return StreamSupport.stream(collection.listIndexes().spliterator(), false).map(d -> {
      DbTreeValue val = new DbTreeValue(value.getMongoDatabase(), (String)d.get("name"), TreeValueType.INDEX);
      val.setCollectionName(value.getDisplayValue());
      return new TreeItem<>(val, new FontAwesomeIconView(FontAwesomeIcon.ASTERISK));
    }).collect(Collectors.toList());
  }

  public void initialize(TreeView<DbTreeValue> treeView, MainFrameController mainFrameController) {
    this.treeView = treeView;
    this.mainFrameController = mainFrameController;
    treeView.setRoot(new TreeItem<>());
    treeView.setCellFactory(tv -> new TreeDbCell());
    buildDbContextMenu();
    buildCollectionContextMenu();
    buildIndexContextMenu();
    buildConnectContextMenu();
  }

  private void buildConnectContextMenu() {
    MenuItem createDb = new MenuItem("Create db...");
    createDb.setOnAction(this::onCreateNewDb);
    MenuItem disconnect = new MenuItem("Disconnect");
    disconnect.setOnAction(this::onDisconnectDb);
    connectContextMenu = new ContextMenu(createDb, disconnect);
  }

  private void buildIndexContextMenu() {
    MenuItem dropIndex = new MenuItem("Drop Index...");
    dropIndex.setOnAction(this::onDropIndex);
    indexContextMenu = new ContextMenu(dropIndex);
  }

  private void buildCollectionContextMenu() {
    MenuItem openShell = new MenuItem("Open Shell");
    openShell.setOnAction(this::onOpenShell);
    MenuItem renameCollection = new MenuItem("Rename collection...");
    renameCollection.setOnAction(this::onReanameCollection);
    MenuItem copyCollection = new MenuItem("Copy collection...");
    copyCollection.setOnAction(this::onCopyCollection);
    MenuItem removeAllDocs = new MenuItem("Remove All Documents...");
    removeAllDocs.setOnAction(this::onRemoveAllDocuments);
    MenuItem dropCollection = new MenuItem("Drop Collection...");
    dropCollection.setOnAction(this::onDropCollection);
    collectionContextMenu = new ContextMenu(openShell, renameCollection, copyCollection, removeAllDocs, dropCollection);
  }

  private void onReanameCollection(ActionEvent actionEvent) {
    TreeItem<DbTreeValue> selectedItem = treeView.getSelectionModel().getSelectedItem();
    if (selectedItem == null) {
      return;
    }

    DbTreeValue value = selectedItem.getValue();
    TextInputDialog dialog = new TextInputDialog(value.getDisplayValue());
    dialog.setContentText("New collection name:");
    dialog.setHeaderText("Rename collection");
    dialog.showAndWait().ifPresent(targetCollection -> {
      MongoCollection<Document> collection = value.getMongoDatabase().getMongoDb().getCollection(value.getDisplayValue());
      collection.renameCollection(new MongoNamespace(value.getMongoDatabase().getName(), targetCollection));
      ((DynamicTreeItem)selectedItem.getParent()).reload();
    });
  }

  private void onCopyCollection(ActionEvent actionEvent) {
    TreeItem<DbTreeValue> selectedItem = treeView.getSelectionModel().getSelectedItem();
    if (selectedItem == null) {
      return;
    }

    DbTreeValue value = selectedItem.getValue();
    String sourceCollectionName = value.getDisplayValue();
    TextInputDialog dialog = new TextInputDialog(sourceCollectionName + "_copy");
    dialog.setContentText("New collection name:");
    dialog.setHeaderText("Copy collection");
    dialog.showAndWait().ifPresent(targetCollection -> {
      new CopyCollectionHelper(value.getMongoDatabase(), sourceCollectionName, targetCollection).copy();
      ((DynamicTreeItem)selectedItem.getParent()).reload();
      popupService.showInfo(String.format("Collection %s copied to %s", sourceCollectionName, targetCollection));
    });
  }

  private void buildDbContextMenu() {
    MenuItem openShell = new MenuItem("Open Shell");
    openShell.setOnAction(this::onOpenShell);
    MenuItem createCollection = new MenuItem("Create collection");
    createCollection.setOnAction(this::onCreateNewCollection);
    MenuItem dropCollection = new MenuItem("Drop db");
    dropCollection.setOnAction(this::onDropDB);
    dbContextMenu = new ContextMenu(openShell, createCollection, dropCollection);
  }

  private void onOpenShell(ActionEvent actionEvent) {
    mainFrameController.openTab();
  }

  public void onCreateNewDb(ActionEvent ev) {
    TreeItem<DbTreeValue> selectedItem = treeView.getSelectionModel().getSelectedItem();
    if (selectedItem == null || selectedItem.getValue().getValueType() != TreeValueType.CONNECTION) {
      return;
    }

    TextInputDialog dialog = new TextInputDialog();
    dialog.setContentText("Enter Name:");
    dialog.setHeaderText("Create new db");
    dialog.showAndWait().ifPresent(r -> selectedItem.getChildren()
        .add(createDbItem(selectedItem.getValue().getMongoConnection().createMongoDB(dialog.getResult()))));
  }

  public void onDisconnectDb(ActionEvent ev) {
    TreeItem<DbTreeValue> selectedItem = treeView.getSelectionModel().getSelectedItem();
    if (selectedItem == null) {
      return;
    }

    selectedItem.getValue().getMongoConnection().close();
    removeFromRoot(selectedItem);
  }

  private void onCreateNewCollection(ActionEvent ev) {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setContentText("Enter Name:");
    dialog.setHeaderText("Create new collection");
    dialog.showAndWait().ifPresent(r -> {
      DbTreeValue value = treeView.getSelectionModel().getSelectedItem().getValue();
      value.getMongoDatabase().createCollection(dialog.getResult());
      reloadSelectedTreeItem();
    });
  }

  private void onDropIndex(ActionEvent ev) {
    DbTreeValue value = treeView.getSelectionModel().getSelectedItem().getValue();
    String indexName = value.getDisplayValue();

    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setHeaderText("Drop index " + indexName);
    alert.setContentText("Are you sure?");
    alert.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
      value.getMongoDatabase().dropIndex(value.getCollectionName(), indexName);
      reloadSelectedTreeItem();
    });
  }

  private void onDropCollection(ActionEvent ev) {
    DbTreeValue value = treeView.getSelectionModel().getSelectedItem().getValue();
    String collectionName = value.getDisplayValue();

    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setHeaderText("Drop collection " + collectionName);
    alert.setContentText("Are you sure?");
    alert.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
      value.getMongoDatabase().dropCollection(collectionName);
      reloadSelectedTreeItem();
      popupService.showInfo(String.format("Collection '%s' dropped", collectionName));
    });
  }

  private void onRemoveAllDocuments(ActionEvent ev) {
    DbTreeValue value = treeView.getSelectionModel().getSelectedItem().getValue();
    String collectionName = value.getDisplayValue();

    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setHeaderText("Remove all documents form " + collectionName);
    alert.setContentText("Are you sure?");
    alert.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
      value.getMongoDatabase().removeAllDocuments(collectionName);
      popupService.showInfo(String.format("All documents from collection '%s' removed", collectionName));
    });
  }

  private void onDropDB(ActionEvent ev) {
    DbTreeValue value = treeView.getSelectionModel().getSelectedItem().getValue();
    String dbName = value.getDisplayValue();

    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setHeaderText("Drop database " + dbName);
    alert.setContentText("Are you sure?");
    alert.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
      value.getMongoDatabase().drop();
      reloadSelectedTreeItem();
      popupService.showInfo(String.format("Database '%s' dropped", dbName));
    });
  }

  public void addDbConnect(MongoDbConnection mongoDbConnection) {
    DbTreeValue connectTreeValue =
        new DbTreeValue(mongoDbConnection, mongoDbConnection.getConnectionSettings().getHost());
    DynamicTreeItem item = new DynamicTreeItem(connectTreeValue, new FontAwesomeIconView(FontAwesomeIcon.SERVER),
        executor, popupService, tv -> buildDbList(tv.getHostConnect().getMongoConnection()));
    item.setOnFiled(() -> removeFromRoot(item));
    item.setExpanded(true);
    treeView.getRoot().getChildren().add(item);
  }

  private boolean removeFromRoot(TreeItem<DbTreeValue> selectedItem) {
    return treeView.getRoot().getChildren().remove(selectedItem);
  }


  private class TreeDbCell extends TreeCell<DbTreeValue> {
    public void treeViewClicked(MouseEvent ev) {
      if (ev.getClickCount() == 2) {
        // don't process click on triangle
        EventTarget target = ev.getTarget();
        if (target instanceof Node && !"arrow".equals(((Node)target).getStyleClass())) {
          TreeItem<DbTreeValue> selectedItem = treeView.getSelectionModel().getSelectedItem();
          if (selectedItem != null && selectedItem.getValue().getValueType() == TreeValueType.COLLECTION) {
            mainFrameController.openTab();
            ev.consume();
          }
        }
      }
    }

    @Override
    protected void updateItem(DbTreeValue item, boolean empty) {
      super.updateItem(item, empty);

      setupGraphic();
      setOnMouseClicked(this::treeViewClicked);

      if (!empty) {
        setText(item.toString());
        setupContextMenu(item);
        setupTooltip(item);
      }
      else {
        setText(null);
        setContextMenu(null);
        setTooltip(null);
      }
    }

    private void setupTooltip(DbTreeValue item) {
      TreeValueType valueType = item.getValueType();
      if (valueType == TreeValueType.COLLECTION) {
        setTooltip(uiBuilder.loadDBCollectionInfoTooltip(item.getCollectionDetails()));
      } else {
        setTooltip(null);
      }
    }

    private void setupContextMenu(DbTreeValue item) {
      TreeValueType valueType = item.getValueType();
      if (valueType == TreeValueType.CONNECTION) {
        setContextMenu(connectContextMenu);
      }
      else if (valueType == TreeValueType.DATABASE) {
        setContextMenu(dbContextMenu);
      }
      else if (valueType == TreeValueType.COLLECTION) {
        setContextMenu(collectionContextMenu);
      }
      else if (valueType == TreeValueType.INDEX) {
        setContextMenu(indexContextMenu);
      }
      else {
        setContextMenu(null);
      }
    }

    private void setupGraphic() {
      TreeItem<DbTreeValue> treeItem = getTreeItem();
      if (treeItem != null && treeItem.getGraphic() != null) {
        setGraphic(treeItem.getGraphic());
      }
      else {
        setGraphic(null);
      }
    }
  }
}
