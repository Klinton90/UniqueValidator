package Klinton90.UniqueValidator;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.springframework.stereotype.Service;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.ValidationException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Service
public class UniqueValidator extends SessionAwareConstraintValidator<Object> implements ConstraintValidator<Unique, Object>{

    private String[] fields;

    public void initialize(Unique annotation){
        this.fields = annotation.fields();
    }

    public boolean isValidInSession(Object value, ConstraintValidatorContext context){
        if(value == null){
            return true;
        }

        String field = _countRows3(value);
        if(field != null){
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(field)
                    .addConstraintViolation();
            return false;
        }

        return true;
    }

    //region Guide
    private String _countRows(Object value) {
        Class clazz = value.getClass();
        ClassMetadata meta = getSessionFactory().getClassMetadata(clazz);
        String idName = meta.getIdentifierPropertyName();
        Serializable idValue = meta.getIdentifier(value, (SessionImplementor)getTmpSession());

        String[] fields = this.fields.length > 0 ? this.fields : _extractFieldsFromObject(value);
        for(String fieldName : fields){
            Object fieldValue = meta.getPropertyValue(value, fieldName);
            if(_hasRecord(clazz, fieldName, fieldValue, idName, idValue)){
                return fieldName;
            }
        }

        return null;
    }

    private boolean _hasRecord(Class clazz, String fieldName, Object fieldValue, String idName, Serializable idValue){
        DetachedCriteria criteria = DetachedCriteria.forClass(clazz)
                .setProjection(Projections.rowCount())
                .add(Restrictions.eq(fieldName, fieldValue));

        if(idValue != null){
            criteria.add(Restrictions.ne(idName, idValue));
        }

        Number count = (Number)criteria
                .getExecutableCriteria(getTmpSession())
                .list().iterator().next();

        return count.intValue() > 0;
    }

    private String[] _extractFieldsFromObject(Object value){
        ArrayList<String> fields = new ArrayList<>();
        for(Field field: value.getClass().getDeclaredFields()){
            if(field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).unique()){
                fields.add(field.getName());
            }
        }

        return fields.toArray(new String[0]);
    }
    //endregion

    //region Reflection
    private int _countRows2(Object value){
        DetachedCriteria criteria = DetachedCriteria.forClass(value.getClass());

        if(fields.length > 0){
            for(String fieldName: fields){
                try{
                    Field field = value.getClass().getDeclaredField(fieldName);
                    _prepareCriteria(field, value, criteria);
                }catch(NoSuchFieldException e){
                    throw new ValidationException("Field '" + fieldName +"' do not exist for class: '" + value.getClass() + "'");
                }
            }
        }else{
            for(Field field: value.getClass().getDeclaredFields()){
                _prepareCriteria(field, value, criteria);
            }
        }

        criteria.setProjection(Projections.rowCount());
        List results = criteria.getExecutableCriteria(getTmpSession()).list();
        Number count = (Number)results.iterator().next();
        return count.intValue();
    }

    private void _prepareCriteria(Field field, Object value, DetachedCriteria criteria){
        try{
            if(field.isAnnotationPresent(Id.class)){
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                if(fieldValue != null){
                    criteria.add(Restrictions.ne(field.getName(), fieldValue));
                }
            }else if(field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).unique()){
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                criteria.add(Restrictions.eq(field.getName(), fieldValue));
            }
        }catch(IllegalAccessException e){
            throw new ValidationException("Cannot extract class metadata for class: '" + value.getClass() + "'");
        }
    }
    //endregion

    //region Experimental Reflection
    private ArrayList<String[]> _getFieldsFromUniqueConstraint(Object value){
        ArrayList<String[]> result = new ArrayList<>();

        if(value.getClass().isAnnotationPresent(Table.class)){
            Table ta = value.getClass().getAnnotation(Table.class);
            for(UniqueConstraint uc : ta.uniqueConstraints()){
                result.add(uc.columnNames());
            }
        }

        return result;
    }

    private ArrayList<String[]> _prepareFields(){
        ArrayList<String[]> result = new ArrayList<>();

        for(String fieldSet: fields){
            result.add(fieldSet.replaceAll("\\s","").split(","));
        }

        return result;
    }

    private ArrayList<String[]> _extractFieldsFromObject2(Object value){
        ArrayList<String[]> result = new ArrayList<>();
        for(Field field: value.getClass().getDeclaredFields()){
            if(field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).unique()){
                String[] fieldSet = {field.getName()};
                result.add(fieldSet);
            }
        }

        return result;
    }


    private boolean _hasRecord2(Object value, String[] fieldNames, String idName, Serializable idValue, ClassMetadata meta){
        DetachedCriteria criteria = DetachedCriteria
                .forClass(value.getClass())
                .setProjection(Projections.rowCount());

        for(String fieldName: fieldNames){
            criteria.add(Restrictions.eq(fieldName, meta.getPropertyValue(value, fieldName)));
        }

        if(idValue != null){
            criteria.add(Restrictions.ne(idName, idValue));
        }

        Number count = (Number)criteria
                .getExecutableCriteria(getTmpSession())
                .list().iterator().next();

        return count.intValue() > 0;
    }

    private String _countRows3(Object value) {
        ClassMetadata meta = getSessionFactory().getClassMetadata(value.getClass());
        String idName = meta.getIdentifierPropertyName();
        Serializable idValue = meta.getIdentifier(value, (SessionImplementor)getTmpSession());

        ArrayList<String[]> fieldSets;
        if(this.fields.length > 0){
            fieldSets = _prepareFields();
        }else{
            fieldSets = _getFieldsFromUniqueConstraint(value);
            fieldSets.addAll(_extractFieldsFromObject2(value));
        }

        for(String[] fieldSet : fieldSets){
            if(_hasRecord2(value, fieldSet, idName, idValue, meta)){
                return fieldSet[0];
            }
        }

        return null;
    }
    //endregion
}