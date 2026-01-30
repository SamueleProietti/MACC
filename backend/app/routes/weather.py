import os
import requests
from flask import Blueprint, request, jsonify

weather_bp = Blueprint('weather', __name__)

# ⚠️ IMPORTANTE: Imposta questa variabile d'ambiente su Cloud Run!
OPENWEATHER_API_KEY = os.environ.get('OPENWEATHER_API_KEY', '182738ed665b62581689fc150adcd8a5')

@weather_bp.route('/v1/weather', methods=['GET'])
def get_weather():
    lat = request.args.get('lat')
    lng = request.args.get('lng')

    if not lat or not lng:
        return jsonify({'error': 'Lat/Lng missing'}), 400

    url = f"https://api.openweathermap.org/data/2.5/weather?lat={lat}&lon={lng}&appid={OPENWEATHER_API_KEY}&units=metric"

    try:
        resp = requests.get(url)
        data = resp.json()
        
        # Mappiamo i codici meteo complessi in stati di gioco semplici
        # https://openweathermap.org/weather-conditions
        w_id = data['weather'][0]['id']
        condition = "clear"
        
        if 200 <= w_id <= 232: condition = "storm"
        elif 300 <= w_id <= 531: condition = "rain"
        elif 600 <= w_id <= 622: condition = "snow"
        elif 701 <= w_id <= 781: condition = "fog"
        elif w_id == 800: condition = "clear"
        elif w_id > 800: condition = "cloudy"

        return jsonify({
            'condition': condition,
            'temp': data['main']['temp'],
            'city': data['name']
        })
    except Exception as e:
        print(f"Weather Error: {e}")
        # Fallback in caso di errore: diciamo che è sereno
        return jsonify({'condition': 'clear', 'temp': 20.0, 'city': 'Unknown'}), 200