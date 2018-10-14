package Klinton90.UniqueValidator;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
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
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UniqueValidator extends SessionAwareConstraintValidator<Object> implements ConstraintValidator<Unique, Object>, MessageSourceAware{

    private UniqueColumn[] columns;
    private MessageSource _messageSource;

    @Override
    public void initialize(Unique annotation){
        columns = annotation.columns();
    }

    public boolean isValidInSession(Object value, ConstraintValidatorContext context){
        if(value == null){
            return true;
        }

        Map<String, Object> fieldMap = _countRows2(value);
        if(fieldMap != null){
            String message = getMessageSource().getMessage(context.getDefaultConstraintMessageTemplate(), null, LocaleContextHolder.getLocale());
            Map.Entry<String, Object> field = fieldMap.entrySet().iterator().next();
            context.unwrap(HibernateConstraintValidatorContext.class)
                    .addExpressionVariable("name", value.getClass().getSimpleName())
                    .addExpressionVariable("fullName", value.getClass().getName())
                    .addExpressionVariable("field", field.getKey())
                    .addExpressionVariable("value", field.getValue())
                    .addExpressionVariable("allFields", StringUtils.join(fieldMap.keySet(), ", "))
                    .addExpressionVariable("values", StringUtils.join(fieldMap.values(), ", "))
                    .buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(field.getKey())
                    .addConstraintViolation()
                    .disableDefaultConstraintViolation();

            return false;
        }

        return true;
    }

    private List<String[]> _getFieldsFromUniqueConstraint(Object value){
        if(value.getClass().isAnnotationPresent(Table.class)){
            return Arrays.stream(value.getClass().getAnnotation(Table.class).uniqueConstraints())
                    .map(UniqueConstraint::columnNames)
                    .collect(toListOrEmpty());
        }
        return new ArrayList<>();
    }

    private List<String[]> _prepareColumns(){
        return Arrays.stream(columns)
                .map(UniqueColumn::fields)
                .collect(toListOrEmpty());
    }

    private List<String[]> _extractFieldsFromObject(Object value){
        return Arrays.stream(value.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).unique())
                .map(field -> new String[]{field.getName()})
                .collect(toListOrEmpty());
    }

    private boolean _hasRecord(Object value, Map<String, Object> fieldMap, ClassMetadata meta){
        DetachedCriteria criteria = DetachedCriteria
                .forClass(value.getClass())
                .setProjection(Projections.rowCount());

        for(Map.Entry<String, Object> fieldEntry: fieldMap.entrySet()){
            criteria.add(Restrictions.eq(fieldEntry.getKey(), fieldEntry.getValue()));
        }

        Serializable idValue = meta.getIdentifier(value, (SessionImplementor)getTmpSession());
        if(idValue != null){
            criteria.add(Restrictions.ne(meta.getIdentifierPropertyName(), idValue));
        }

        Number count = (Number)criteria
                .getExecutableCriteria(getTmpSession())
                .list().iterator().next();

        return count.intValue() > 0;
    }

    private TreeMap<String, Object> _countRows(Object value) {
        List<String[]> fieldSets;
        ClassMetadata meta = getSessionFactory().getClassMetadata(value.getClass());

        if(columns.length > 0){
            fieldSets = _prepareColumns();
        }else{
            fieldSets = _getFieldsFromUniqueConstraint(value);
            fieldSets.addAll(_extractFieldsFromObject(value));
        }

        for(String[] fieldSet : fieldSets){
            TreeMap<String, Object> fieldMap = new TreeMap<>();

            Arrays.stream(fieldSet).forEach(fieldName -> {
                Object val = meta.getPropertyValue(value, fieldName);

                UniqueColumn col = Arrays.stream(columns)
                        .filter(column -> column.fields().length == 1 && column.fields()[0].equals(fieldName) && Arrays.asList(column.orValue()).contains(val.toString()))
                        .findFirst().orElse(null);

                if(col == null){
                    fieldMap.put(fieldName, val);
                }
            });

            if(_hasRecord(value, fieldMap, meta)){
                return fieldMap;
            }
        }

        return null;
    }

    private Map<String, Object> _countRows2(Object value){
        List<Map<String, Object>> fieldValueCombos;
        ClassMetadata meta = getSessionFactory().getClassMetadata(value.getClass());

        if(columns.length > 0){
            fieldValueCombos = _prepareColumns(meta, value);
        }else{
            fieldValueCombos = _getFieldsFromUniqueConstraint(meta, value);
            fieldValueCombos.addAll(_extractFieldsFromObject(meta, value));
        }

        for(Map<String, Object> fieldMap: fieldValueCombos){
            if(_hasRecord(value, fieldMap, meta)){
                return fieldMap;
            }
        }

        return null;
    }

    //@Unique(columns = @UniqueColumn(fields = {"user", "timesheetCategory"}))
    private List<Map<String, Object>> _prepareColumns(ClassMetadata meta, Object value){
        return Arrays.stream(columns)
                .map(column -> {
                    if(column.fields().length == 1){
                        Map<String, Object> result = new HashMap<>();
                        String fieldName = column.fields()[0];
                        Object val = meta.getPropertyValue(value, fieldName);

                        if(!Arrays.asList(column.orValue()).contains(val.toString())){
                            result.put(fieldName, val);
                        }

                        return result;
                    }

                    return fieldSetToMap(column.fields(), meta, value);
                })
                .filter(item -> item.size() > 0)
                .collect(toListOrEmpty());
    }

    //@Unique on fields
    private List<Map<String, Object>> _extractFieldsFromObject(ClassMetadata meta, Object value){
        return Arrays.stream(value.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).unique())
                .map(field -> {
                    String fieldName = field.getName();
                    Object val = meta.getPropertyValue(value, fieldName);

                    Map<String, Object> map = new HashMap<>();
                    map.put(fieldName, val);
                    return map;
                })
                .collect(toListOrEmpty());
    }

    //@Table(name="users", uniqueConstraints = {@UniqueConstraint(columnNames = {"email", "password"}), @UniqueConstraint(columnNames = "role")})
    private List<Map<String, Object>> _getFieldsFromUniqueConstraint(ClassMetadata meta, Object value){
        if(value.getClass().isAnnotationPresent(Table.class)){
            return Arrays.stream(value.getClass().getAnnotation(Table.class).uniqueConstraints())
                    .map(columnConstraint -> fieldSetToMap(columnConstraint.columnNames(), meta, value))
                    .collect(toListOrEmpty());
        }
        return new ArrayList<>();
    }

    private Map<String, Object> fieldSetToMap(String[] fieldSet, ClassMetadata meta, Object value){
        return Arrays.stream(fieldSet).collect(Collectors.toMap(
                Function.identity(),
                fieldName -> meta.getPropertyValue(value, fieldName)
        ));
    }

    //region getters/setters
    @Override
    public void setMessageSource(MessageSource messageSource){
        this._messageSource = messageSource;
    }

    public MessageSource getMessageSource(){
        return this._messageSource;
    }
    //endregion

    private static <T> Collector<T, ?, List<T>> toListOrEmpty() {
        return Collectors.collectingAndThen(
                Collectors.toList(),
                l -> l.isEmpty() ? new ArrayList<>() : l
        );
    }

}