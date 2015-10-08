package dk.magenta.editonline;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.cmr.site.SiteVisibility;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.springframework.context.ApplicationEvent;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;

import java.util.Arrays;
import java.util.List;

public class EditOnlineOffsiteBehaviour extends AbstractLifecycleBean
        implements NodeServicePolicies.OnCreateChildAssociationPolicy, NodeServicePolicies.OnDeleteChildAssociationPolicy {
    private static final String EDIT_ONLINE_SITE_SHORT_NAME = "eods";

    protected NodeService nodeService;
    protected Repository repositoryHelper;
    protected FileFolderService fileFolderService;
    protected SiteService siteService;
    protected PolicyComponent policyComponent;
    protected TransactionService transactionService;
    protected AuthorityService authorityService;

    public void init() {
        policyComponent.bindAssociationBehaviour(
                NodeServicePolicies.OnCreateChildAssociationPolicy.QNAME,
                ContentModel.TYPE_FOLDER,
                ContentModel.ASSOC_CONTAINS,
                new JavaBehaviour(this, "onCreateChildAssociation", Behaviour.NotificationFrequency.TRANSACTION_COMMIT));
        policyComponent.bindAssociationBehaviour(
                NodeServicePolicies.OnDeleteChildAssociationPolicy.QNAME,
                ContentModel.TYPE_FOLDER,
                ContentModel.ASSOC_CONTAINS,
                new JavaBehaviour(this, "onDeleteChildAssociation", Behaviour.NotificationFrequency.TRANSACTION_COMMIT));
    }

    @Override
    protected void onBootstrap(ApplicationEvent event) {
        runAsSystemInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Object>() {
            @Override
            public Object execute() throws Throwable {
                bootstrap();
                return null;
            }
        });
    }

    private void bootstrap() {
        if (!siteService.hasSite(EDIT_ONLINE_SITE_SHORT_NAME)) {
            SiteInfo site = siteService.createSite("site-dashboard", EDIT_ONLINE_SITE_SHORT_NAME, "Edit Online Default Site",
                    "Edit Online Default Site", SiteVisibility.PUBLIC);

            authorityService.addAuthority(
                    Arrays.asList(
                            siteService.getSiteRoleGroup(site.getShortName(), "SiteCollaborator"),
                            siteService.getSiteRoleGroup(site.getShortName(), "SiteConsumer")),
                    "GROUP_EVERYONE");
        }

        NodeRef companyHome = repositoryHelper.getCompanyHome();
        List<FileInfo> folders = fileFolderService.listFolders(companyHome);
        for (FileInfo folder : folders) {
            addNodeToSite(folder.getNodeRef());
        }
    }

    private void addNodeToSite(NodeRef nodeRef) {
        runAsSystemInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Object>() {
            @Override
            public Object execute() throws Throwable {
                if (nodeRef.equals(siteService.getSiteRoot())) {
                    // Don't allow the "Sites" folder to be added to the Edit Online
                    // site. That would create a cycle. Besides, it should
                    // already possible to "Edit Online" for those nodes.
                    return null;
                }

                NodeRef docLibNodeRef = getSiteDocLibNodeRef();
                String name = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
                // Check if node already exists on site
                if (nodeService.getChildByName(docLibNodeRef, ContentModel.ASSOC_CONTAINS, name) == null) {
                    nodeService.addChild(docLibNodeRef, nodeRef, ContentModel.ASSOC_CONTAINS,
                            QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI,
                                    QName.createValidLocalName(name)));
                }
                return null;
            }
        });
    }

    private void removeNodeFromSite(NodeRef nodeRef) {
        runAsSystemInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Object>() {
            @Override
            public Object execute() throws Throwable {
                NodeRef docLibNodeRef = getSiteDocLibNodeRef();
                nodeService.removeChild(docLibNodeRef, nodeRef);
                return null;
            }
        });
    }

    private NodeRef getSiteDocLibNodeRef() {
        return siteService.getContainer(EDIT_ONLINE_SITE_SHORT_NAME, SiteService.DOCUMENT_LIBRARY);
    }

    @Override
    protected void onShutdown(ApplicationEvent event) {

    }

    @Override
    public void onCreateChildAssociation(ChildAssociationRef childAssocRef, boolean isNewNode) {
        if (nodeService.exists(childAssocRef.getChildRef()) && childAssocRef.getParentRef().equals(repositoryHelper.getCompanyHome())) {
            addNodeToSite(childAssocRef.getChildRef());
        }
    }

    @Override
    public void onDeleteChildAssociation(ChildAssociationRef childAssocRef) {
        if (nodeService.exists(childAssocRef.getChildRef()) && childAssocRef.getParentRef().equals(repositoryHelper.getCompanyHome())) {
            removeNodeFromSite(childAssocRef.getChildRef());
        }
    }

    private <T> T runAsSystemInTransaction(RetryingTransactionHelper.RetryingTransactionCallback<T> callback) {
        return AuthenticationUtil.runAsSystem(new AuthenticationUtil.RunAsWork<T>() {
            @Override
            public T doWork() throws Exception {
                return transactionService.getRetryingTransactionHelper().doInTransaction(callback);
            }
        });
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setRepositoryHelper(Repository repositoryHelper) {
        this.repositoryHelper = repositoryHelper;
    }

    public void setFileFolderService(FileFolderService fileFolderService) {
        this.fileFolderService = fileFolderService;
    }

    public void setSiteService(SiteService siteService) {
        this.siteService = siteService;
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }
}
