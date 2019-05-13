/*
 * This file is part of Alpine.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package alpine.util;

import org.junit.Assert;
import org.junit.Test;

public class UuidUtilTest {

    @Test
    public void insertHyphensTest() {
        Assert.assertEquals("9aa395cb-8e91-4e77-aeaf-1a41d85bffa8", UuidUtil.insertHyphens("9aa395cb8e914e77aeaf1a41d85bffa8"));
    }

    @Test
    public void stripHyphensTest() {
        Assert.assertEquals("9aa395cb8e914e77aeaf1a41d85bffa8", UuidUtil.stripHyphens("9aa395cb-8e91-4e77-aeaf-1a41d85bffa8"));
    }

    @Test
    public void isValidUuidTest() {
        Assert.assertFalse(UuidUtil.isValidUUID("9aa395cb8e914e77aeaf1a41d85bffa8"));
        Assert.assertFalse(UuidUtil.isValidUUID("9AA395CB-8E91-4E77-AEAF-1A41D85BFFA8"));
        Assert.assertTrue(UuidUtil.isValidUUID("00000000-0000-0000-0000-000000000000"));
        Assert.assertTrue(UuidUtil.isValidUUID("9aa395cb-8e91-4e77-aeaf-1a41d85bffa8"));
    }
}
