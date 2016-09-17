package Klinton90.UniqueValidator;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import javax.validation.ConstraintValidatorContext;
import javax.validation.ValidationException;

@Component
@Transactional
public abstract class SessionAwareConstraintValidator<T>{

    @Autowired
    public SessionFactory sessionFactory;

    private boolean openedNewTransaction;
    private Session tmpSession;

    public SessionAwareConstraintValidator(){}

    public abstract boolean isValidInSession(T value, ConstraintValidatorContext context);

    public boolean isValid(T value, ConstraintValidatorContext context){
        openTmpSession();
        boolean result = isValidInSession(value, context);
        closeTmpSession();
        return result;
    }

    private void openTmpSession(){
        Session currentSession;
        try{
            currentSession = getSessionFactory().getCurrentSession();
        }catch(HibernateException e){
            throw new ValidationException("Unable to determine current Hibernate session", e);
        }
        if(currentSession.getTransaction().getStatus() != TransactionStatus.ACTIVE){
            currentSession.beginTransaction();
            openedNewTransaction = true;
        }

        try {
            tmpSession = getSessionFactory().openSession();
        }catch(HibernateException e){
            throw new ValidationException("Unable to open temporary session", e);
        }
    }

    private void closeTmpSession(){
        if(openedNewTransaction){
            try{
                getSessionFactory().getCurrentSession().getTransaction().commit();
            }catch(HibernateException e){
                throw new ValidationException("Unable to commit transaction for temporary session", e);
            }
        }
        try{
            tmpSession.close();
        }catch(HibernateException e){
            throw new ValidationException("Unable to close temporary Hibernate session", e);
        }
    }

    //region getters/setters
    public SessionFactory getSessionFactory(){
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory){
        this.sessionFactory = sessionFactory;
    }

    public Session getTmpSession(){
        return tmpSession;
    }
    //endregion
}