
package com.tofik.coolexoplayer.exoplayer.cool.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


@Retention(RetentionPolicy.SOURCE)  //
public @interface Sorted {

  Order order() default Order.ASCENDING;

  enum Order {
    ASCENDING, DESCENDING, UNSORTED
  }
}
