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
package mongofx.service.settings;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Settings {
  private List<ConnectionSettings> connections;

  public List<ConnectionSettings> getConnections() {
    if (connections == null) {
      connections = new ArrayList<>();
    }
    return connections;
  }

  public void setConnections(List<ConnectionSettings> connections) {
    this.connections = connections;
  }

  @JsonIgnore
  public boolean isEmpty() {
    return connections == null || connections.isEmpty();
  }
}
