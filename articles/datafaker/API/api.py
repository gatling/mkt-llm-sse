from flask import Flask, request, jsonify
from werkzeug.security import generate_password_hash

app = Flask(__name__)

@app.route('/hello', methods=['POST'])
def hello():
    # Get data from request
    data = request.get_json()
    
    # Check if all required fields are present
    required_fields = ['firstname', 'lastname']
    if not all(field in data for field in required_fields):
        return jsonify({
            'status': 'error',
            'message': 'Missing required fields'
        }), 400
    
    firstname = data['firstname']
    lastname = data['lastname']

    # In a real application, you would save the user to a database here
    # For this example, we'll just return a success response
    
    return jsonify({
        'status': 'success',
        'message': 'Hello '+firstname+' '+lastname
    }), 201

if __name__ == '__main__':
    app.run(debug=True)