/*
 * Copyright (c) 2013, Swedish Institute of Computer Science
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of The Swedish Institute of Computer Science nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE SWEDISH INSTITUTE OF COMPUTER SCIENCE BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/* Description:
 * TODO:
 * */

package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import play.db.ebean.Model;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.validation.Length;

import controllers.Utils;

@Entity
@Table(name = "data_point_string", uniqueConstraints = {@UniqueConstraint(columnNames = {
        "stream_id", "timestamp"})})
public class DataPointString extends DataPoint {
    /**
	 *
	 */
    private static final long serialVersionUID = -6502881310122879601L;

    @Transient
    public final static int maxLength = 160;
    @Length(min = 1, max = maxLength)
    public String data;

    public static Model.Finder<Long, DataPointString> find =
            new Model.Finder<Long, DataPointString>(Long.class, DataPointString.class);

    public DataPointString() {
        this(null, null, null);
        // TODO Auto-generated constructor stub
    }

    public DataPointString(Stream stream, String data, Long timestamp) {
        this.stream = stream;
        this.data = data;
        this.timestamp = timestamp;
    }

    public DataPointString add() {
        // DataPointDouble dataPoint = new DataPoint(stream, data, timestamp);
        if (stream != null && data != null) {
            if (data.length() > maxLength) {
                data.substring(0, maxLength - 1);
            }
            this.save();
            return this;
        }
        return null;
    }

    @Override
    public String toTSV() {
        return timestamp + "\t" + data;
    }

    public String getData() {
        return data;
    }
}
