/*
 * Copyright 2016 Code Above Lab LLC
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

package com.codeabovelab.dm.mail.service;

/**
 * Appere when when system detect io problem, like broken connection or timeout.
 */
public class MailIOException extends MailSenderException {
    public MailIOException(String message) {
        super(message);
    }

    public MailIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public MailIOException(Throwable cause) {
        super(cause);
    }
}
