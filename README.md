# Spring UniqueValidator

Simple Spring UniqueValidator for Database fields.
Based on [this example](https://developer.jboss.org/wiki/AccessingtheHibernateSessionwithinaConstraintValidator?_sscc=t)

#### Warning
As validator requires Database interaction, it consumes `SessionFactory`.
Basically many guides do not recommend using `SessionFactory` outside `Repository` classes.
Moreover, Spring/Spring Boot handle transactions/sessions for you. 
However, often it could be beneficially having generic UniqueValidator for resolving routine validation.
So it is up to you to include such dependency in your project.

## Features
1) Multiple ways to set UniqueConstraint in Domain class
2) Validator can read Constraints from well known `javax.persistence.*` Annotations
3) Can handle both single and multi column Constraints
4) Even though provided Annotation/Validator has `Target = Type` (applies to class level only), 
it provides `FieldError` that can be used in Controller.
5) Advanced ValidationMessage control.

## Quick start

### Prepare your application
As it has been mentioned before, proposed implementation requires `SessionFactory`. 
So as first required step is providing `SessionFactory` bean for your application.
For Spring Boot 2 actions required:
1) Provide `SessionFactory` bean.
```
@EnableConfigurationProperties
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public SessionFactory sessionFactory(EntityManagerFactory emf) {
        if (emf.unwrap(SessionFactory.class) == null) {
            throw new NullPointerException("factory is not a hibernate factory");
        }
        return emf.unwrap(SessionFactory.class);
    }

}
```
2) Enable `CurrentSession` in `application.properties` file.
```
spring.jpa.properties.hibernate.current_session_context_class = org.springframework.orm.hibernate5.SpringSessionContext
```
 - HINT! As we are working with Spring Boot, I assume that we will be using `@Validated` annotation in our actions. 
 When we are doing so, by default, application will be validating Domain twice: once in Controller, 
 second time before saving Domain to DataBase. For simple validators that is not a big deal.
 But UniqueValidator will be also hitting DataBase twice. 
 To prevent that, good idea will be using one more application property (validation will be done only in Controller):
```
spring.jpa.properties.javax.persistence.validation.mode = none
```

### Unique Constraint
Now we have to decide what way for setting Unique Constraints we will be using in our app.
There are 3 possible ways for setting Constraints with `UniqueValidator`. 
Provided examples are silly, but it works for basic explanation.

#### 1. `fields` property. 
Provide `fields` property into Annotation body.
```
@Table(name="users")
@Unique(fields = {"email", "role"})
public class User implements Serializable{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    @Column(name = "email)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "role")
    private String role;
}
```
In that case, Validator checks 2 columns for Unique values. 
If you enable hibernate SQL logging, for each successful request you will see 2 DB queries:
```
select count(*) as y0_ from users this_ where this_.email=?
select count(*) as y0_ from users this_ where this_.role=?
```
But let's say you need MultiColumn constraint. In that case just put 2 column names separated by comma as 1 entry:
```
@Table(name="users")
@Unique(fields = {"email, password", "role"})
public class User implements Serializable{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    @Column(name = "email)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "role")
    private String role;
}
```
Example SQL log in this case:
```
select count(*) as y0_ from users this_ where this_.email=? and this_.password=?
select count(*) as y0_ from users this_ where this_.role=?
```

#### 2. `@Column(unique = true)` Annotation
That is possible to read Constraints from `javax.persistence.Column` Annotation. 
By default, setting that property will add Unique indexes for DataBase fields (if create/update action allowed).
But it does not provide Validation to Domain. 
When it is saved and unique record exists in DataBase, Exception will be thrown.
Using UniqueValidator we can reuse already created Domains without significant redesign.
```
@Unique
@Table(name="users")
public class User implements Serializable{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    @Column(name = "email, unique = true)
    private String email;

    @Column(name = "password", unique = true)
    private String password;

    @Column(name = "role")
    private String role;
}
```
Please note! In that case using Multi Column Constraint is not possible, as that is not allowed by `javax.persistence` schema.

#### 3. `@Table(name="users", uniqueConstraints = {})` Annotation
That is possible to read Constraints from `javax.persistence.Table` Annotation.
By default, setting that property will add Unique indexes for DataBase fields (if create/update action allowed).
But it does not provide Validation to Domain. 
When it is saved and unique record exists in DataBase, Exception will be thrown.
Using UniqueValidator we can reuse already created Domains without significant redesign.
```
@Unique
@Table(name="users", uniqueConstraints = {@UniqueConstraint(columnNames = {"email", "password"}), @UniqueConstraint(columnNames = "role")})
public class User implements Serializable{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    @Column(name = "email, unique = true)
    private String email;

    @Column(name = "password", unique = true)
    private String password;

    @Column(name = "role")
    private String role;
}
```
In that case we added 2 Unique Constraints:
 - for `role` column;
 - for `email` and `password` columns (Multi Column Constraint).
 
Please note! `fields` property has higher priority then anything else. 
`@Column` and `@Table` annotations can work together, i.e. constraints will be picked up from both.

### Default ValidationMessage
When Validator is instantiated in code, it puts 6 MessageParameters:
 - `name` - entity name
 - `field` - field name (same as returned by FieldError)
 - `value` - non-unique value (that belongs to `field`)
 - `fullName` - class (e.g. `app.domain.user`)
 - `fields` - all field names for Multi Column Constraint separated by comma
 - `values` - all field names for Multi Column Constraint separated by comma in same order as fields
 
You can use these values in direct `messages.properties` file.
```
Klinton90.unique=Record ${name}:[${fields}:${values}] already exists
```
Please note! Unfortunately, there is no way to return same set of fields as arguments of `FieldError`.

### Using FieldError
Basic validation handler for Controllers:
```
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Map<String, Object>> processValidationError(MethodArgumentNotValidException ex) {
    HashMap<String, Object> result = new HashMap<>();
    
    BindingResult bindingResult = ex.getBindingResult();
    FieldError fieldError = bindingResult.getFieldError();
    
    result.put("field", fieldError.getField());
    result.put("message", fieldError.getDefaultMessage());
    result.put("data", bindingResult.getTarget());

    return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
}
```
Using `FieldError` we can get field name (that is useful, if you want to stick error message to some field on your page),
or get `DomainObject` with provided values.

Please note! In case of Multi Column Constraint only 1 column will be returned as `FieldError`.
Unfortunately there is no way to control which column to return. 
Fields will be sorted alphabetically and first will be set as `FieldError`.