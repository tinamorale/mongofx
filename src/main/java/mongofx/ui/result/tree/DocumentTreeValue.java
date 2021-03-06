// This file is part of MongoFX.
//
// MongoFX is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
// MongoFX is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with MongoFX.  If not, see <http://www.gnu.org/licenses/>.

//
// Copyright (c) Andrey Dubravin, 2015
//
package mongofx.ui.result.tree;

import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

/**
 * @author daa
 *
 */
public class DocumentTreeValue {
  private final Object value;
  private final String key;

  public DocumentTreeValue(String key, Object value) {
    this.key = key;
    this.value = value;
  }

  public Document getDocument() {
    return (Document)value;
  }

  @Override
  public String toString() {
    if (isDocument()) {
      return "";
    }
    if (isList()) {
      return "[]";
    }
    return String.valueOf(value);
  }

	public boolean isDocument() {
		return value instanceof Document;
	}

	public boolean isList() {
		return value instanceof List;
	}

  public Object getDisplayValue() {
    if (isTopLevel()) {
      return String.format("{%d fields}", getDocument().size());
    }
    return value;
  }

  public String getKey() {
    if (isTopLevel() && isDocument()) {
      Object id = ((Document)value).get("_id");
      if (id != null) {
        if (id instanceof ObjectId) {
          return "ObjectId(" + ((ObjectId)id).toHexString() + ")";
        }
        return id.toString();
      }
      return "";
    }
    return key;
  }

  public Object getValue() {
    return value;
  }

  public String getTypeDescription() {
    if (value != null) {
      return value.getClass().getSimpleName();
    }
    return null;
  }

  public boolean isTopLevel() {
    return key == null;
  }
}
