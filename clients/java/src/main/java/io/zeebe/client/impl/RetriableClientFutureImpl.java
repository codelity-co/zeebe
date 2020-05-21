/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl;

import io.grpc.stub.StreamObserver;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class RetriableClientFutureImpl<R, T> extends ZeebeClientFutureImpl<R, T> {

  private final Predicate<Throwable> retryPredicate;
  private final Consumer<StreamObserver<T>> retryAction;

  public RetriableClientFutureImpl(
      final Predicate<Throwable> retryPredicate, final Consumer<StreamObserver<T>> retryAction) {
    this(brokerResponse -> null, retryPredicate, retryAction);
  }

  public RetriableClientFutureImpl(
      final Function<T, R> responseMapper,
      final Predicate<Throwable> retryPredicate,
      final Consumer<StreamObserver<T>> retryAction) {
    super(responseMapper);

    Objects.requireNonNull(retryPredicate, "Expected to have non-null retry predicate.");
    Objects.requireNonNull(retryAction, "Expected to have non-null retry action.");
    this.retryPredicate = retryPredicate;
    this.retryAction = retryAction;
  }

  @Override
  public void onError(final Throwable throwable) {
    if (retryPredicate.test(throwable)) {
      retryAction.accept(this);
    } else {
      super.onError(throwable);
    }
  }
}