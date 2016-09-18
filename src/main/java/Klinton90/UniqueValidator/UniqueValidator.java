package Klinton90.UniqueValidator;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import javax.persistence.Column;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.stereotype.Service;

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

        TreeMap<String, Object> fieldMap = _countRows(value);
        if(fieldMap != null){
            Map.Entry<String, Object> field = fieldMap.entrySet().iterator().next();
            context.unwrap(HibernateConstraintValidatorContext.class)
                    .addExpressionVariable("name", value.getClass().getSimpleName())
                    .addExpressionVariable("fullName", value.getClass().getName())
                    .addExpressionVariable("field", field.getKey())
                    .addExpressionVariable("value", field.getValue())
                    .addExpressionVariable("fields", StringUtils.join(fieldMap.keySet(), ", "))
                    .addExpressionVariable("values", StringUtils.join(fieldMap.values(), ", "))
                    .buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(field.getKey())
                    .addConstraintViolation()
                    .disableDefaultConstraintViolation();

            return false;
        }

        return true;
    }

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

    private ArrayList<String[]> _extractFieldsFromObject(Object value){
        ArrayList<String[]> result = new ArrayList<>();
        for(Field field: value.getClass().getDeclaredFields()){
            if(field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).unique()){
                String[] fieldSet = {field.getName()};
                result.add(fieldSet);
            }
        }

        return result;
    }


    private boolean _hasRecord(Object value, Map<String, Object> fieldMap, String idName, Serializable idValue, ClassMetadata meta){
        DetachedCriteria criteria = DetachedCriteria
                .forClass(value.getClass())
                .setProjection(Projections.rowCount());

        for(Map.Entry<String, Object> fieldEntry: fieldMap.entrySet()){
            criteria.add(Restrictions.eq(fieldEntry.getKey(), fieldEntry.getValue()));
        }

        if(idValue != null){
            criteria.add(Restrictions.ne(idName, idValue));
        }

        Number count = (Number)criteria
                .getExecutableCriteria(getTmpSession())
                .list().iterator().next();

        return count.intValue() > 0;
    }

    private TreeMap<String, Object> _countRows(Object value) {
        ClassMetadata meta = getSessionFactory().getClassMetadata(value.getClass());
        String idName = meta.getIdentifierPropertyName();
        Serializable idValue = meta.getIdentifier(value, (SessionImplementor)getTmpSession());

        ArrayList<String[]> fieldSets;
        if(this.fields.length > 0){
            fieldSets = _prepareFields();
        }else{
            fieldSets = _getFieldsFromUniqueConstraint(value);
            fieldSets.addAll(_extractFieldsFromObject(value));
        }

        for(String[] fieldSet : fieldSets){
            TreeMap<String, Object> fieldMap = new TreeMap<>();
            for(String fieldName: fieldSet){
                fieldMap.put(fieldName, meta.getPropertyValue(value, fieldName));
            }
            if(_hasRecord(value, fieldMap, idName, idValue, meta)){
                return fieldMap;
            }
        }

        return null;
    }
}