"use strict";

if (Alfresco !== undefined && Alfresco.doclib !== undefined && Alfresco.doclib.Actions !== undefined) {

  // Everything is a valid location for online edit.
  Alfresco.util.validLocationForOnlineEdit = function () { return true; };

  var old_onActionEditOnline = Alfresco.doclib.Actions.prototype.onActionEditOnline;

  var new_onActionEditOnline = function new_onActionEditOnline (oldRecord) {
    var record = Alfresco.util.deepCopy(oldRecord);
    var loc = record.location;
    if (loc.site === undefined || loc.container === undefined) {
      loc.site = {
        name: "eods"
      };
      loc.container = {
        name: "documentLibrary",
        type: "cm:folder"
      };
      if (loc.repoPath !== undefined) {
        loc.path = loc.repoPath;
      } else {
        // 5.0.c doesn't have repoPath available, so use webdavUrl
        var webdavPath = record.webdavUrl;
        webdavPath = webdavPath.slice(1).split("/");
        webdavPath.shift();
        webdavPath.pop();
        loc.path = "/" + webdavPath.join("/");
      }
    }
    record.onlineEditUrl = Alfresco.util.onlineEditUrl(this.doclistMetadata.custom.vtiServer, loc);

    //Try to use alternate edit online URL: http://{host}:{port}/{context}/_IDX_SITE_{site_uuid}/_IDX_NODE_{document_uuid}/{document_name}
    // This is because some paths contain invalid SPP characters.
    Alfresco.util.Ajax.request(
        {
          method: Alfresco.util.Ajax.GET,
          url: Alfresco.constants.PROXY_URI + "/api/sites/" + record.location.site.name,
          successCallback: {
            fn: function (response) {
              var siteUUID = response.json.node.split("/").pop();
              var docUUID = record.nodeRef.split("/").pop();

              // Replace invalid SPP characters in displayName with
              // underscore.
              if ((/[~"#%&*:<>?\/\\{|}]/).test(record.displayName)) {
                record.displayName = record.displayName.replace(/[~"#%&*:<>?\/\\{|}]/, "_");
              }

              // Use site name instead of _IDX_SITE_ + siteUUID in the path.
              // This is because of a problem encountered on Alfresco 5.0.c
              // where Office would open documents in read-only mode when
              // using _IDX_SITE_ + siteUUID in the path.
              record.onlineEditUrl = record.onlineEditUrl.split(record.location.site.name)[0] + record.location.site.name + "/_IDX_NODE_" + docUUID + "/" + record.displayName;
              if (record.onlineEditUrl.length > 260) {
                var ext = record.displayName.split(".").pop();
                var recordName = record.displayName.split(".")[0];
                var exceed = record.onlineEditUrl.length - 260;
                record.onlineEditUrl = record.onlineEditUrl.replace(record.displayName, recordName.substring(0, recordName.length - exceed - 1) + "." + ext);
              }
              old_onActionEditOnline.call(this, record);
            },
            scope: this
          },
          failureCallback: {
            fn: function (response) {
              old_onActionEditOnline.call(this, record);
            },
            scope: this
          }
    });
  };

  // Redefine the existing edit online actions
  // Unfortunately, we can't just override
  // Alfresco.doclib.Actions.prototype.onActionEditOnline because at this
  // point, it has already be copied into the inheriting classes' prototypes
  if (Alfresco.DocumentActions !== undefined) {
    Alfresco.DocumentActions.prototype.onActionEditOnline = new_onActionEditOnline;
  }
  if (Alfresco.FolderActions !== undefined) {
    Alfresco.FolderActions.prototype.onActionEditOnline = new_onActionEditOnline;
  }
  if (Alfresco.DocumentList !== undefined) {
    Alfresco.DocumentList.prototype.onActionEditOnline = new_onActionEditOnline;
  }
}