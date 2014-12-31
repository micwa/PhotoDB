/**
 * This file is part of PhotoDB - MySQL client/GUI for accessing photo databases
 * 
 * Copyright (C) 2014 by Michael Wang
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *   
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */
package photo.db;

import java.sql.Types;

// Represents all supported data types - each enum represents the
// type (in Java), which is mapped to the type in SQL. The latter can be 
// obtained by calling getSqlType().
public enum DataType
{
    INT(Types.INTEGER), BOOLEAN(Types.BOOLEAN), DOUBLE(Types.DOUBLE),
    LONG(Types.BIGINT), STRING(Types.VARCHAR), DATE(Types.DATE),
    TIME(Types.TIME), BIN_STREAM(Types.BLOB);

    private int sqlType;

    private DataType(int sqlType)
    {
        this.sqlType = sqlType;
    }
    
    // Returns the SQL data type it is associated with
    public int getSqlType()
    {
        return sqlType;
    }
}
