package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.DateUtil;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Collections;
import java.util.stream.Collectors;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 *
 * @author xyang
 */
@Entity
@Table(indexes = {@Index(columnList="user_id")})

public class UserNotification implements Serializable {
    public enum Type {
        ASSIGNROLE, REVOKEROLE, CREATEDV, CREATEDS, CREATEACC, SUBMITTEDDS, RETURNEDDS, 
        PUBLISHEDDS, REQUESTFILEACCESS, GRANTFILEACCESS, REJECTFILEACCESS, FILESYSTEMIMPORT, 
        CHECKSUMIMPORT, CHECKSUMFAIL, CONFIRMEMAIL, APIGENERATED, INGESTCOMPLETED, INGESTCOMPLETEDWITHERRORS, 
        PUBLISHFAILED_PIDREG, WORKFLOW_SUCCESS, WORKFLOW_FAILURE, STATUSUPDATED, DATASETCREATED;
        
        public String getDescription() {
            return BundleUtil.getStringFromBundle("notification.typeDescription." + this.name());
        }
        
        public long flagValue() {
            return 1 << this.ordinal();
        }
        
        public static List<Type> fromFlag(Long flag) {
            final List<Type> types = new ArrayList<Type>();
            if (flag == null) {
                return types;
            }
            for (final Type t : values()) {
                if ((flag & t.flagValue()) > 0) {
                    types.add(t);
                }
            }
            return types;
        }
        
        public static Long toFlag(final List<Type> types) {
            if (types == null || types.isEmpty()) {
                return null;
            }
            long flag = 0;
            for (final Type t : types) {
                flag |= t.flagValue();
            }
            return flag;
        }

        public static Set<Type> tokenizeToSet(String tokens) {
            if (tokens == null || tokens.isEmpty()) {
                return Collections.<Type>emptySet();
            }
            return Collections.list(new StringTokenizer(tokens, ",")).stream()
                .map(token -> Type.valueOf(((String) token).trim()))
                .collect(Collectors.toSet());
        }
    };
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn( nullable = false )
    private AuthenticatedUser user;
    @ManyToOne
    /** Requestor now has a more general meaning of 'actor' - the person who's action is causing the emails.
     * The original use of that was for people requesting dataset access
     * This is also now used for DATASETCREATED messages where it indicates who created the dataset
    */
    @JoinColumn( nullable = true )
    private AuthenticatedUser requestor;
    private Timestamp sendDate;
    private boolean readNotification;
    
    @Enumerated
    @Column( nullable = false )
    private Type type;
    private Long objectId;

    @Transient
    private boolean displayAsRead;
    
    @Transient 
    String roleString;

    private boolean emailed;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AuthenticatedUser getUser() {
        return user;
    }

    public void setUser(AuthenticatedUser user) {
        this.user = user;
    }
        
    public AuthenticatedUser getRequestor() {
        return requestor;
    }

    public void setRequestor(AuthenticatedUser requestor) {
        this.requestor = requestor;
    }

    public String getSendDate() {
        return new SimpleDateFormat("MMMM d, yyyy h:mm a z").format(sendDate);
    }

    public void setSendDate(Timestamp sendDate) {
        this.sendDate = sendDate;
    }

    public boolean isReadNotification() {
        return readNotification;
    }

    public void setReadNotification(boolean readNotification) {
        this.readNotification = readNotification;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
    
    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }
    
    @Transient 
    private Object theObject;

    public Object getTheObject() {
        return theObject;
    }

    public void setTheObject(Object theObject) {
        this.theObject = theObject;
    }
    
        
    public boolean isDisplayAsRead() {
        return displayAsRead;
    }

    public void setDisplayAsRead(boolean displayAsRead) {
        this.displayAsRead = displayAsRead;
    }

    public boolean isEmailed() {
        return emailed;
    }

    public void setEmailed(boolean emailed) {
        this.emailed = emailed;
    }    
    
    public String getRoleString() {
        return roleString;
    }

    public void setRoleString(String roleString) {
        this.roleString = roleString;
    }

    public String getLocaleSendDate() {
        return DateUtil.formatDate(sendDate);
    }
}
