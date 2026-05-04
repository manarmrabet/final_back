package com.example.CWMS.audit;



import java.lang.annotation.*;
/*l'annotation ne s'applique qu'aux méthodes*/
@Target(ElementType.METHOD)
//l'annotation doit être disponible à l'exécution pour que Spring puisse la lire
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    String action();
    String entityType() default "";
}
