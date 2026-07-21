package io.github.lutzseverino.cardo.identity.productauth;

@FunctionalInterface
public interface ProductRequestPolicy {

  void authorize(ProductRequestRules rules);
}
