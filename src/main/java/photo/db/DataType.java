package photo.db;

import java.sql.Types;

// Represents all supported data types
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
