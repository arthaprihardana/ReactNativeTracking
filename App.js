/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 * @flow
 */

import React, { Component } from 'react';
import {
  Platform,
  StyleSheet,
  Text,
  View,
  Button,
  TouchableOpacity,
  TextInput,
  Keyboard
} from 'react-native';
import ToastExample from './ToastExample'
import {
  TrackerService
} from './TrackerExample'

const instructions = Platform.select({
  ios: 'Press Cmd+R to reload,\n' +
    'Cmd+D or shake for dev menu',
  android: 'Double tap R on your keyboard to reload,\n' +
    'Shake or press menu button for dev menu',
});

export default class App extends Component<{}> {
  constructor(props) {
    super(props);
    this.state = {
      transport_id: "",
      email: "",
      password: "",
      tracking: false
    }
  }
  

  render() {
    return (
      <View style={{ flex: 1, padding: 20 }}>
        <Text style={styles.welcome}>
          Welcome to Firebase Tracking {'\n'} For React Native!
        </Text>
        <View syle={{ width: '100%' }}>
          <View>
            <Text>Name / ID</Text>
          </View>
          <View>
            <TextInput
              style={{height: 40, borderColor: 'gray', borderWidth: 1}}
              onChangeText={(text) => this.setState({ transport_id: text })}
              value={this.state.transport_id}
              underlineColorAndroid={'transparent'}
            />
          </View>
        </View>
        <View syle={{ width: '100%' }}>
          <View>
            <Text>Email</Text>
          </View>
          <View>
            <TextInput
              style={{height: 40, borderColor: 'gray', borderWidth: 1}}
              onChangeText={(text) => this.setState({ email: text })}
              value={this.state.email}
              underlineColorAndroid={'transparent'}
            />
          </View>
        </View>
        <View syle={{ width: '100%' }}>
          <View>
            <Text>Password</Text>
          </View>
          <View>
            <TextInput
              style={{height: 40, borderColor: 'gray', borderWidth: 1}}
              onChangeText={(text) => this.setState({ password: text })}
              value={this.state.password}
              underlineColorAndroid={'transparent'}
              secureTextEntry={true}
            />
          </View>
        </View>
        <TouchableOpacity onPress={() => {
            {/* TrackerService.connect('user2', 'user2@email.com', '123') */}
            Keyboard.dismiss();
            {/* this.setState({ tracking : false }) */}
            {/* console.log('this.state.tracking ==> ', this.state.tracking) */}
            if(this.state.tracking) {
              TrackerService.stopLocationService()
              this.setState({ tracking : false })
            } else {
              TrackerService.connect(
                this.state.transport_id,
                this.state.email,
                this.state.password,
                val => { this.setState({ tracking: val }) },
                err => { this.setState({ tracking: err }) }
              )
            }

            
          }}>
          <View style={{ width: '100%', backgroundColor: '#2196F3', height: 40, marginTop: 20, alignItems: 'center', justifyContent: 'center' }}>
            <Text style={{ color: '#FFF'}}>
              { this.state.tracking ? "Stop Tracking" : "Start Tracking" }
            </Text>
          </View>
        </TouchableOpacity>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
});
