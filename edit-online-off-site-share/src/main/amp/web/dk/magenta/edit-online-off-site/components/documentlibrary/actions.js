"use strict";

if (Alfresco !== undefined && Alfresco.doclib !== undefined && Alfresco.doclib.Actions !== undefined) {
  var old_onActionEditOnline = Alfresco.doclib.Actions.prototype.onActionEditOnline;

  var new_onActionEditOnline = function new_onActionEditOnline (record) {
    var newRecord = Alfresco.util.deepCopy(record);
    var loc = newRecord.location;
    if (loc.site === undefined || loc.container === undefined) {
      loc.site = {
        name: "eods"
      };
      loc.container = {
        name: "documentLibrary",
        type: "cm:folder"
      };
      loc.path = loc.repoPath;
    }
    newRecord.onlineEditUrl = Alfresco.util.onlineEditUrl(this.doclistMetadata.custom.vtiServer, loc);
    old_onActionEditOnline.call(this, newRecord);
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